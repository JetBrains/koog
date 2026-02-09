@file:OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent.context

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMReadSession
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock
import kotlin.jvm.JvmName

/**
 * Represents the context for an AI agent LLM, managing tools, prompt handling, and interaction with the
 * environment and execution layers. It provides mechanisms for concurrent read and write operations
 * through sessions, ensuring thread safety.
 *
 * @property tools A list of tool descriptors available for the context.
 * @property toolRegistry A registry that contains metadata about available tools.
 * @property prompt The current LLM prompt being used or updated in write sessions.
 * @property model The current LLM model being used or updated in write sessions.
 * @property responseProcessor The current response processor being used or updated in write sessions.
 * @property promptExecutor The [PromptExecutor] responsible for performing operations on the current prompt.
 * @property environment The environment that manages tool execution and interaction with external dependencies.
 * @property clock The clock used for timestamps of messages
 */
public expect class AIAgentLLMContext internal constructor(
    delegate: AIAgentLLMContextImpl
) : AIAgentLLMContextAPI {

    /**
     * Constructs a new instance of `AIAgentLLMContext` with the provided parameters.
     *
     * @param tools A list of tools described by [ToolDescriptor] that the agent can interact with.
     * @param toolRegistry A registry of available tools, defaulting to an empty [ToolRegistry].
     * @param prompt The initial prompt used in the context, represented by a [Prompt] instance.
     * @param model The language model used for processing prompts and generating responses.
     * @param responseProcessor An optional [ResponseProcessor] for handling and processing model responses.
     * @param promptExecutor Responsible for executing the logic for prompt processing in the context.
     * @param environment The operational environment of the AI agent, represented by an [AIAgentEnvironment].
     * @param config Configuration settings for the AI agent, encapsulated in an [AIAgentConfig].
     * @param clock A clock instance for managing time-related operations within the context.
     */
    public constructor(
        tools: List<ToolDescriptor>,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    )

    internal val delegate: AIAgentLLMContextImpl

    @get:JvmName("toolRegistry")
    override val toolRegistry: ToolRegistry

    @property:DetachedPromptExecutorAPI
    @get:JvmName("promptExecutor")
    override val promptExecutor: PromptExecutor

    @get:JvmName("environment")
    @InternalAgentsApi
    override val environment: AIAgentEnvironment

    @get:JvmName("config")
    @InternalAgentsApi
    override val config: AIAgentConfig

    @get:JvmName("clock")
    @InternalAgentsApi
    override val clock: Clock

    /**
     * List of current tools associated with this agent context.
     */
    @DetachedPromptExecutorAPI
    @get:JvmName("tools")
    override var tools: List<ToolDescriptor>
        @InternalAgentsApi set

    /**
     * LLM currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    @get:JvmName("model")
    override var model: LLModel
        @InternalAgentsApi set

    /**
     * Response processor currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    @get:JvmName("responseProcessor")
    public override var responseProcessor: ResponseProcessor?
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
    override var prompt: Prompt

    /**
     * Updates the current `AIAgentLLMContext` with a new prompt and ensures thread-safe access using a read lock.
     *
     * @param prompt The new [Prompt] to be set for the context.
     */
    public override suspend fun withPrompt(block: Prompt.() -> Prompt)

    /**
     * Creates a deep copy of this LLM context.
     *
     * @return A new instance of [AIAgentLLMContext] with deep copies of mutable properties.
     */
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
    ): AIAgentLLMContext

    /**
     * Executes a write session on the [AIAgentLLMContext], ensuring that all active write and read sessions
     * are completed before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T

    /**
     * Executes a read session within the [AIAgentLLMContext], ensuring concurrent safety
     * with active write session and other read sessions.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public override suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T

    /**
     * Returns the current prompt used in the LLM context.
     *
     * @return The current [Prompt] instance.
     */
    public override fun copy(
        tools: List<ToolDescriptor>,
        prompt: Prompt,
        model: LLModel,
        responseProcessor: ResponseProcessor?,
        promptExecutor: PromptExecutor,
        environment: AIAgentEnvironment,
        config: AIAgentConfig,
        clock: Clock
    ): AIAgentLLMContext
}
