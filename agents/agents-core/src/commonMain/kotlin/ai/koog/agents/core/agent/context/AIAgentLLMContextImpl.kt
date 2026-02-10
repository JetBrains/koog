@file:OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class, ExperimentalAtomicApi::class)
@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.utils.RWLock
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Implementation of [AIAgentLLMContextAPI] with read-write lock based session management.
 *
 * ### Writer Starvation
 * The underlying [RWLock] allows writer starvation: continuous read requests
 * can prevent writers from ever acquiring the lock.
 *
 * ### Deadlock Avoidance Trade-offs
 * The underlying [RWLock] uses a non-reentrant `Mutex`, so nested lock acquisition from the same
 * coroutine (e.g., calling `writeSession` or `readSession` from within an active `writeSession`)
 * would deadlock. Two opt-in parameters exist specifically to avoid this:
 *
 * - **`writeSession(reuseActiveSession = true)`** — reuses the currently active write session
 *   without re-acquiring the write lock. This is intended for interceptors that
 *   need to mutate state during an already-active write session.
 *   **Trade-off:** there is no caller/owner verification — *any* coroutine can call this while a
 *   write session is active and mutate the session without holding the lock. This is accepted
 *   because the alternative (re-acquiring the non-reentrant lock) would deadlock.
 *
 * - **`readSession(readUncommitted = true)`** — reads from the uncommitted write session state
 *   without acquiring any lock. This is intended for code running inside a `writeSession` that
 *   needs to read the current (uncommitted) state.
 *   **Trade-off:** *any* coroutine can call this while a write session is active and observe
 *   uncommitted state that may later be rolled back. This is accepted because acquiring the
 *   read lock from within a write session would deadlock.
 *
 * By default, both `readSession` and `writeSession` acquire proper locks for full isolation.
 */
internal class AIAgentLLMContextImpl(
    override var tools: List<ToolDescriptor>,
    override val toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    override var prompt: Prompt,
    override var model: LLModel,
    override var responseProcessor: ResponseProcessor?,
    override val promptExecutor: PromptExecutor,
    override val environment: AIAgentEnvironment,
    override val config: AIAgentConfig,
    override val clock: Clock
) : AIAgentLLMContextAPI {
    public override suspend fun withPrompt(block: Prompt.() -> Prompt): Unit = rwLock.withWriteLock {
        this.prompt = prompt.block()
    }

    public override suspend fun copy(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext = rwLock.withReadLock {
        AIAgentLLMContext(
            tools = tools,
            toolRegistry = toolRegistry,
            prompt = prompt,
            model = model,
            promptExecutor = promptExecutor,
            environment = environment,
            config = config,
            clock = clock,
            responseProcessor = responseProcessor
        )
    }

    /**
     * Read-write lock for coordinating access to mutable state.
     * - Writers acquire exclusive access via write lock methods
     * - Readers acquire shared access via withReadLock
     * - Multiple readers can read concurrently
     * - Writers block readers and other writers
     */
    private val rwLock = RWLock()

    /**
     * Tracks the currently active write session.
     * Used by [writeSession] with `reuseActiveSession = true` to reuse the session without
     * re-acquiring the lock, and by [readSession] with `readUncommitted = true` to read
     * uncommitted state when called within a write session.
     */
    private val activeWriteSession: AtomicReference<AIAgentLLMWriteSession?> = AtomicReference(null)

    /**
     * Executes a write session with exclusive access.
     *
     * Only one writer can execute at a time (serialized via [RWLock] write lock).
     * Changes are committed atomically when the block completes successfully.
     *
     * @param reuseActiveSession When `true` and a write session is already active, the block
     *   executes on the existing session **without re-acquiring the write lock**. This prevents
     *   deadlocks when interceptors need to mutate state during an active write session.
     *   **Caveat:** no caller/owner verification is performed — any coroutine can reuse the
     *   active session. See class-level documentation for the rationale.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(
        reuseActiveSession: Boolean,
        block: suspend AIAgentLLMWriteSession.() -> T
    ): T {
        if (reuseActiveSession) {
            val active = activeWriteSession.load()
            if (active != null) {
                return active.block()
            }
            // No active session — fall through to normal write
        }

        return rwLock.withWriteLock {
            val session = AIAgentLLMWriteSession(
                environment,
                promptExecutor,
                tools,
                toolRegistry,
                prompt,
                model,
                responseProcessor,
                config,
                clock
            )

            activeWriteSession.store(session)

            try {
                val result = session.block()

                // Commit: update state with all changes from the session
                this.prompt = session.prompt
                this.tools = session.tools
                this.model = session.model
                // TODO: should we update responseProcessor?

                result
            } finally {
                activeWriteSession.store(null)
                session.close()
            }
        }
    }

    /**
     * Executes a read session with shared access.
     *
     * Multiple readers can execute concurrently; readers are blocked while a writer holds the lock.
     *
     * @param readUncommitted When `true` and a write session is currently active, the block
     *   reads directly from the uncommitted write session state **without acquiring any lock**.
     *   This prevents deadlocks when reading from within an active write session.
     *   **Caveat:** any coroutine (not just the write-session owner) calling with
     *   `readUncommitted = true` will observe uncommitted state that may be rolled back.
     *   See class-level documentation for the rationale.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(
        readUncommitted: Boolean,
        block: suspend AIAgentLLMReadSession.() -> T
    ): T {
        if (readUncommitted) {
            val activeSession = activeWriteSession.load()
            if (activeSession != null) {
                val uncommitted = AIAgentLLMReadSession(
                    activeSession.tools, promptExecutor, activeSession.prompt,
                    activeSession.model, activeSession.responseProcessor, config
                )
                return uncommitted.use { block(it) }
            }
        }
        return rwLock.withReadLock {
            val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, responseProcessor, config)
            session.use { block(it) }
        }
    }

    /**
     * CAVEAT: Reads mutable state (tools, prompt, model, responseProcessor) without acquiring any lock.
     * If another coroutine is in a writeSession modifying these fields, copy can observe partially updated/inconsistent state.
     */
    public override fun copy(
        tools: List<ToolDescriptor>,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock,
    ): AIAgentLLMContext {
        return AIAgentLLMContext(
            tools,
            toolRegistry,
            prompt,
            model,
            responseProcessor,
            promptExecutor,
            environment,
            config,
            clock
        )
    }
}
