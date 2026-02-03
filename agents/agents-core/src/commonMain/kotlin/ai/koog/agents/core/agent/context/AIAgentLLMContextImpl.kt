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
 * ### Writer Starvation
 * The underlying RWLock allows writer starvation: continuous read requests
 * can prevent writers from ever acquiring the lock.
 *
 * ### Lock Upgrade Not Supported
 * Calling writeSession from within readSession will deadlock. Always
 * structure code to acquire write access at the outermost level if writes
 * are needed.
 *
 * ### Uncommitted State Visibility
 * When a writeSession is active, ALL concurrent readSession calls (not just
 * those from the same logical session) will see the uncommitted state.
 * This is a trade-off for preventing a deadlock.
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
     * - Writers acquire exclusive access via withWriteLock
     * - Readers acquire shared access via withReadLock
     * - Multiple readers can read concurrently
     * - Writers block readers and other writers
     * - CAVEAT: Writer starvation possible (see RWLock documentation)
     */
    private val rwLock = RWLock()

    /**
     * Tracks the currently active write session with its ID.
     * Used for session-based reentrancy detection that works across thread boundaries.
     *
     * When a write session is active, ALL readers (regardless of their logical session)
     * read from the uncommitted state without acquiring the read lock. This is a
     * trade-off to prevent deadlock when readSession is called from within writeSession.
     */
    private data class ActiveWriteSession(
        val id: String,
        val session: AIAgentLLMWriteSession
    )

    private val activeWriteSession: AtomicReference<ActiveWriteSession?> = AtomicReference(null)

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
     * Executes a write session with exclusive access.
     *
     * Behavior:
     * - Only one writer can execute at a time (serialized via RWLock.withWriteLock)
     * - Reentrant: if a session with the same sessionId is active, reuses it
     * - Different sessionId waits for the active session to complete
     * - Changes are committed atomically at the end of the session
     * - Works with Java interop by passing sessionId across thread boundaries
     *
     * CAVEAT: Do NOT call writeSession from within readSession - this will deadlock
     * due to lock upgrade not being supported.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(
        sessionId: String,
        block: suspend AIAgentLLMWriteSession.(sessionId: String) -> T
    ): T {
        // Fast path: check if we can reuse active session (same ID = reentrant call)
        activeWriteSession.load()?.let { active ->
            if (active.id == sessionId) {
                // Same session ID - reentrant call, reuse session without acquiring lock
                return active.session.block(sessionId)
            }
        }

        // Slow path: acquire exclusive write access
        return rwLock.withWriteLock {
            // Double-check after acquiring lock (another caller might have set it)
            activeWriteSession.load()?.let { active ->
                if (active.id == sessionId) {
                    // Same session ID - reentrant call
                    return@withWriteLock active.session.block(sessionId)
                }
                // Different ID but we have the lock - previous session should have cleared
                // activeWriteSession before releasing lock, so this shouldn't happen
            }

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

            // Publish session with its ID - now visible to all reentering callers
            activeWriteSession.store(ActiveWriteSession(sessionId, session))

            try {
                session.use {
                    val result = it.block(sessionId)

                    // Commit: update state with all changes from the session
                    this.prompt = it.prompt
                    this.tools = it.tools
                    this.model = it.model

                    result
                }
            } finally {
                // Clear session atomically
                activeWriteSession.store(null)
            }
        }
    }

    /**
     * Executes a read session with shared access to the current state.
     *
     * Behavior:
     * - Multiple readers can execute concurrently (shared read lock)
     * - Readers are blocked while a writer holds the write lock
     * - Readers see a consistent snapshot of all mutable fields
     *
     * CAVEAT: Do NOT call writeSession from within readSession - this will deadlock.
     *
     * CAVEAT: When a write session is active, this method reads from the uncommitted
     * state regardless of whether the caller is logically part of that write session.
     * This means concurrent readers may observe uncommitted changes that could be
     * rolled back if the writer throws an exception.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T {
        // Check if we're inside an active write session - read from it without lock
        activeWriteSession.load()?.let { active ->
            val session = AIAgentLLMReadSession(
                active.session.tools,
                promptExecutor,
                active.session.prompt,
                active.session.model,
                active.session.responseProcessor,
                config
            )
            return session.use { block(it) }
        }

        // Acquire read lock for consistent snapshot of all mutable fields
        return rwLock.withReadLock {
            val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, responseProcessor, config)
            session.use { block(it) }
        }
    }

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
