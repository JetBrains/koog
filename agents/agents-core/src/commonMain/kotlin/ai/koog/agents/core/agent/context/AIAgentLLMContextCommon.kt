@file:OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)

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
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.time.Clock

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
    initialClock: Clock
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
    public val clock: Clock = initialClock

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
     * modifications or new inputs for the language model operations. It is thread-safe, ensuring that updates
     * and access are managed correctly within concurrent environments.
     *
     * This variable can only be modified internally via specific methods, maintaining control over state changes.
     */
    @get:JvmName("prompt")
    public var prompt: Prompt = initialPrompt

    private val rwLock: RWLock = RWLock()

    /**
     * Updates the current `AIAgentLLMContext` with a new prompt and ensures thread-safe access using a read lock.
     *
     * @param block transformation to produce the next [Prompt].
     */
    public open suspend fun withPrompt(block: Prompt.() -> Prompt): Unit = rwLock.withReadLock {
        this.prompt = prompt.block()
    }

    /**
     * Creates a deep copy of this LLM context.
     *
     * @return A new instance of [AIAgentLLMContext] with deep copies of mutable properties.
     */
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
        clock: Clock = this.clock,
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
     * Executes a write session on the [AIAgentLLMContext], ensuring that all active write and read sessions
     * are completed before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
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
     * Executes a read session within the [AIAgentLLMContext], ensuring concurrent safety
     * with active write session and other read sessions.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public open suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T = rwLock.withReadLock {
        val session = AIAgentLLMReadSession(tools, promptExecutor, prompt, model, responseProcessor, config)
        session.use { block(it) }
    }

    /**
     * Returns the current prompt used in the LLM context.
     *
     * @return The current [Prompt] instance.
     */
    public open fun copy(
        tools: List<ToolDescriptor> = this.tools,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: Clock = this.clock
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
