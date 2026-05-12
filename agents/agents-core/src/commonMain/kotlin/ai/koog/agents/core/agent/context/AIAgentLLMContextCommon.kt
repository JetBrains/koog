package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.lock.RWLock
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Common [AIAgentLLMContext] implementation shared across platforms.
 */
public abstract class AIAgentLLMContextCommon internal constructor(
    initialTools: List<ToolDescriptor>,
    initialToolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    initialPrompt: Prompt,
    initialModel: LLModel,
    initialResponseProcessor: ResponseProcessor?,
    initialPromptExecutor: PromptExecutor,
    initialEnvironment: AIAgentEnvironment,
    initialConfig: AIAgentConfig,
    initialClock: KoogClock
) {
    /**
     * A [ToolRegistry] that contains metadata about available tools.
     * */
    @get:JvmName("toolRegistry")
    public val toolRegistry: ToolRegistry = initialToolRegistry

    /**
     * The [PromptExecutor] responsible for performing operations on the current prompt.
     * */
    @property:DetachedPromptExecutorAPI
    @get:JvmName("promptExecutor")
    public val promptExecutor: PromptExecutor = initialPromptExecutor

    /**
     * Represents the execution environment associated with an AI agent within the context of the LLM
     * (Large Language Model) framework.
     */
    @get:JvmName("environment")
    @InternalAgentsApi
    public val environment: AIAgentEnvironment = initialEnvironment

    /**
     * Provides access to the configuration settings for an AI agent within the LLM context.
     */
    @get:JvmName("config")
    @InternalAgentsApi
    public val config: AIAgentConfig = initialConfig

    /**
     * Represents the clock instance used for time-related operations and scheduling within the context.
     */
    @get:JvmName("clock")
    @InternalAgentsApi
    public val clock: KoogClock = initialClock

    /**
     * List of current tools associated with this agent context.
     */
    @DetachedPromptExecutorAPI
    @get:JvmName("tools")
    public var tools: List<ToolDescriptor> = initialTools
        @InternalAgentsApi set

    /**
     * LLM currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    @get:JvmName("model")
    public var model: LLModel = initialModel
        @InternalAgentsApi set

    /**
     * Response processor currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    @get:JvmName("responseProcessor")
    public var responseProcessor: ResponseProcessor? = initialResponseProcessor
        @InternalAgentsApi set

    /**
     * The current prompt used within the `AIAgentLLMContext`.
     *
     * This property defines the main [Prompt] instance used by the context and is updated as needed to reflect
     * modifications or new inputs for the language model operations. It is modified internally only from within
     * [withPrompt] and [writeSession], both of which run under the write portion of an internal read-write lock.
     *
     * Concurrency caveats:
     * - A **direct read** of this property (as well as [tools], [model] and [responseProcessor]) does **not**
     *   acquire the read lock; it returns whatever snapshot happens to be installed. For a consistent view that
     *   is safe against a concurrent [withPrompt] / [writeSession], read it inside a [readSession].
     * - Reads performed from inside a [readSession] or [writeSession] are consistent with the current critical
     *   section.
     */
    @get:JvmName("prompt")
    public var prompt: Prompt = initialPrompt

    private val rwLock: RWLock = RWLock()

    /**
     * Atomically replaces [prompt] with the result of applying [block] to the current prompt while holding the
     * internal write lock. Callers waiting for read or write access will be suspended until the transformation
     * completes.
     *
     * Concurrency warnings:
     * - Must not be invoked from inside another [withPrompt], [writeSession], or [readSession] on the same
     *   context — the underlying lock is **not reentrant** and doing so will deadlock (see [RWLock]).
     * - [block] should be a pure, fast transformation; it runs while holding the write lock and will block all
     *   concurrent readers and writers until it returns.
     *
     * @param block transformation to produce the next [Prompt].
     */
    public open suspend fun withPrompt(block: Prompt.() -> Prompt): Unit = rwLock.withWriteLock {
        this.prompt = prompt.block()
    }

    /**
     * Creates a copy of this LLM context, taking a consistent snapshot of its mutable fields under the
     * internal read lock. Multiple concurrent [copy] / [readSession] calls may proceed in parallel, but any
     * concurrent [writeSession] / [withPrompt] will serialize against them.
     *
     * Concurrency caveat: must not be invoked from inside a [writeSession] or [withPrompt] on the same
     * context — the underlying lock is not reentrant (see [RWLock]).
     *
     * @return A new instance of [AIAgentLLMContext] with deep copies of mutable properties.
     */
    @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    @JvmOverloads
    public open suspend fun copy(
        tools: List<ToolDescriptor> = this.tools,
        toolRegistry: ToolRegistry = this.toolRegistry,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: KoogClock = this.clock,
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
     * Executes a write session on the [AIAgentLLMContext], suspending until all other active write and read
     * sessions on this context complete, then running [block] under exclusive access. At the end of the
     * session, [prompt], [tools] and [model] are overwritten with the values mutated inside the session.
     *
     * Concurrency caveats:
     * - The underlying lock is **not reentrant**. Do not call [writeSession], [readSession], [withPrompt] or
     *   [copy] on the same context from inside [block] — doing so will deadlock (see [RWLock]).
     * - [block] runs while holding the write lock; keep it as short as possible and avoid launching
     *   long-running child coroutines that need to re-acquire this lock.
     * - Cancellation inside [block] is propagated; partial mutations made on the session before cancellation
     *   will not be committed to the outer context.
     */
    @OptIn(ExperimentalStdlibApi::class, InternalAgentsApi::class, DetachedPromptExecutorAPI::class)
    public open suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T =
        rwLock.withWriteLock {
            val session =
                AIAgentLLMWriteSession(
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

            session.use {
                val result = it.block()

                this.prompt = it.prompt
                this.tools = it.tools
                this.model = it.model

                result
            }
        }

    /**
     * Executes a read session on the [AIAgentLLMContext]. Multiple read sessions may run concurrently, while
     * any concurrent [writeSession] or [withPrompt] is serialized against them.
     *
     * Concurrency caveats:
     * - The underlying lock is **not reentrant for writers**: nested [readSession] calls from the same
     *   coroutine are safe, but calling [writeSession] or [withPrompt] from inside a [readSession] will
     *   deadlock (see [RWLock]).
     * - Mutations performed on fields exposed through the session are not persisted back to this context
     *   (unlike [writeSession]).
     */
    @OptIn(ExperimentalStdlibApi::class, DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    public open suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, responseProcessor, config)
        session.use { block(it) }
    }

    /**
     * Creates a non-suspending copy of this LLM context with the given overrides. Unlike the suspending [copy]
     * overload, this variant does **not** acquire the internal read lock and therefore does not guarantee a
     * consistent snapshot if another coroutine is concurrently mutating this context.
     *
     * @param tools The list of [ToolDescriptor] tools to use, or the current tools if not specified.
     * @param prompt The [Prompt] to use, or the current prompt if not specified.
     * @param model The [LLModel] to use, or the current model if not specified.
     * @param responseProcessor The [ResponseProcessor] to use, or the current one if not specified.
     * @param promptExecutor The [PromptExecutor] to use, or the current one if not specified.
     * @param environment The [AIAgentEnvironment] to use, or the current one if not specified.
     * @param config The [AIAgentConfig] to use, or the current configuration if not specified.
     * @param clock The [Clock] to use, or the current clock if not specified.
     * @return A new [AIAgentLLMContext] instance with the specified overrides applied.
     */
    @OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
    public open fun copy(
        tools: List<ToolDescriptor> = this.tools,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: KoogClock = this.clock
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
