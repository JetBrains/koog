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
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.datetime.Clock

/**
 * Implementation of [AIAgentLLMContextAPI] with read-write lock based session management.
 *
 * ### Writer Starvation
 * The underlying lock allows writer starvation: continuous read requests
 * can prevent writers from ever acquiring the lock.
 *
 * ### Active Write Session
 * - [mutateActiveSession] allows interceptors to safely mutate the active
 *   write session without re-acquiring the write lock, preventing deadlocks.
 * - [readSession] calls within an active [writeSession] will read from the uncommitted state
 *   without acquiring a lock, preventing deadlocks when called from within writeSession.
 *
 * ### Uncommitted State Visibility
 * When a writeSession is active, ALL concurrent readSession calls (not just
 * those from the same logical session) will see the uncommitted state.
 * This is a trade-off for preventing deadlock when readSession is called from within writeSession.
 *
 * ### Race Window in Read Fast Path
 * There is a race window where a reader may observe an active write session
 * that commits and closes between the check and the read. The reader will
 * read from the session object's final state (which matches committed state),
 * but technically accesses a closed session. This is safe because the session
 * fields remain accessible after close, but it's a subtle correctness issue.
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
     * Used by [mutateActiveSession] to access the session without re-acquiring the lock,
     * and by [readSession] to read uncommitted state when called within a write session.
     */
    private val activeWriteSession: AtomicReference<AIAgentLLMWriteSession?> = AtomicReference(null)

    public override suspend fun withPrompt(block: Prompt.() -> Prompt) {
        rwLock.withWriteLock {
            this.prompt = prompt.block()
        }
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
    ): AIAgentLLMContext {
        return rwLock.withReadLock {
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
    }

    /**
     * Executes a write session with exclusive access.
     *
     * Only one writer can execute at a time (serialized via [RWLock] write lock).
     * Changes are committed atomically when the block completes successfully.
     *
     * Interceptors that need to mutate state during a write session (e.g., Memory2)
     * should use [mutateActiveSession] instead of nesting writeSession calls.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T {
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
                this.responseProcessor = session.responseProcessor

                result
            } finally {
                activeWriteSession.store(null)
                session.close()
            }
        }
    }

    /**
     * Mutates the currently active write session without re-acquiring the write lock.
     *
     * MUST only be called from within an active [writeSession] (e.g., from pipeline interceptors
     * triggered during an LLM request). Throws [IllegalStateException] if no write session is active.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> mutateActiveSession(block: suspend AIAgentLLMWriteSession.() -> T): T {
        val session = activeWriteSession.load()
            ?: error("mutateActiveSession called outside an active writeSession")
        return session.block()
    }

    /**
     * Executes a read session with shared access to the current state.
     *
     * Behavior:
     * - If there's an active write session, reads from its current (uncommitted) state
     *   without acquiring a lock (prevents deadlock when called from within writeSession)
     * - Otherwise, acquires read lock for consistent snapshot of all mutable fields
     * - Multiple readers can execute concurrently when no write session is active
     *
     * CAVEAT: When a write session is active, this method reads from the uncommitted
     * state regardless of whether the caller is logically part of that write session.
     * This means concurrent readers may observe uncommitted changes that could be
     * rolled back if the writer throws an exception.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T {
        // Check if there is an active write session
        val active = activeWriteSession.load()
        if (active != null) {
            // Read from uncommitted state without lock
            val session = AIAgentLLMReadSession(
                active.tools,
                promptExecutor,
                active.prompt,
                active.model,
                active.responseProcessor,
                config
            )
            return session.use { block(it) }
        }

        // Otherwise, acquire read lock
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
