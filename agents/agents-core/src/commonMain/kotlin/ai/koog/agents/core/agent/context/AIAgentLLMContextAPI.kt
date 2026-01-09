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

/**
 * Annotation for marking APIs as detached prompt executors within the `AIAgentLLMContext`.
 *
 * Using APIs annotated with this requires opting in, as calls to `PromptExecutor` will be disconnected
 * from the agent logic. This means these calls will not affect the agent's state or adhere to the
 * `ToolsConversionStrategy`.
 *
 * This API should be used with caution, as it provides functionality that operates outside the
 * standard agent lifecycle and processing logic.
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Calls to PromptExecutor used from `AIAgentLLMContext` will not be connected to the agent logic, " +
        "and will not impact the agent's state. " +
        "Other than that, `ToolsConversionStrategy` will not be applied. " +
        "Please be cautious when using this API."
)
public annotation class DetachedPromptExecutorAPI

/**
 * API for the [AIAgentLLMContext]
 */
public interface AIAgentLLMContextAPI {
    /**
     * A [ToolRegistry] that contains metadata about available tools.
     * */
    public val toolRegistry: ToolRegistry

    /**
     * The [PromptExecutor] responsible for performing operations on the current prompt.
     * */
    @property:DetachedPromptExecutorAPI
    public val promptExecutor: PromptExecutor

    /**
     * Represents the execution environment associated with an AI agent within the context of the LLM (Large Language Model) framework.
     *
     * This property provides a mechanism for interfacing with an external environment, which allows the agent to perform tasks
     * such as executing tools, reporting issues, and sending termination or result messages. The environment is central
     * to facilitating interactions between the AI agent and its operational surroundings.
     *
     * Marked with [InternalAgentsApi], indicating it is intended for internal use within agent-related implementations
     * and not designed for general application development. Changes to this API may occur without notice.
     */
    @InternalAgentsApi
    public val environment: AIAgentEnvironment

    /**
     * Provides access to the configuration settings for an AI agent within the LLM context.
     *
     * This property encapsulates an instance of [AIAgentConfig], which defines the prompt,
     * execution parameters, and behavior of the agent. It is marked with the `@InternalAgentsApi`
     * annotation, indicating its internal use for agent-related implementations and signaling
     * that it is not intended for public-facing applications.
     *
     * The configuration includes settings such as the prompt definition, model specifications,
     * iteration limits, and strategies to handle missing tools during execution. It plays a
     * critical role in defining how the AI agent processes input, generates output, and interacts
     * with other components of the system.
     *
     * Note: This property is accessible with a custom name `config` when interacting with JVM-based
     * systems, as indicated by the `@get:JvmName("config")` annotation.
     */
    @InternalAgentsApi
    public val config: AIAgentConfig

    /**
     * Represents the clock instance used for time-related operations and scheduling within the
     * `AIAgentLLMContextAPI`. This property is intended for internal use in managing timing and
     * scheduling functionalities across the LLM context.
     *
     * As an `@InternalAgentsApi` element, it is not part of the public API and may be
     * subject to changes, removal, or modifications without notice.
     *
     * Use of this property requires an understanding of its role in the internal infrastructure
     * of the AI agents and should be approached with caution in specialized use cases.
     */
    @InternalAgentsApi
    public val clock: Clock

    /**
     * List of current tools associated with this agent context.
     */
    @DetachedPromptExecutorAPI
    public var tools: List<ToolDescriptor>
        @InternalAgentsApi set

    /**
     * LLM currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    public var model: LLModel
        @InternalAgentsApi set

    /**
     * Response processor currently associated with this context.
     */
    @DetachedPromptExecutorAPI
    public var responseProcessor: ResponseProcessor?
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
    public var prompt: Prompt

    /**
     * Updates the current `AIAgentLLMContext` with a new prompt and ensures thread-safe access using a read lock.
     *
     * @param prompt The new [Prompt] to be set for the context.
     */
    public suspend fun withPrompt(block: Prompt.() -> Prompt)

    /**
     * Creates a deep copy of this LLM context.
     *
     * @return A new instance of [AIAgentLLMContext] with deep copies of mutable properties.
     */
    public suspend fun copy(
        tools: List<ToolDescriptor> = this.tools,
        toolRegistry: ToolRegistry = this.toolRegistry,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: Clock = this.clock,
    ): AIAgentLLMContext

    /**
     * Executes a write session on the [AIAgentLLMContext], ensuring that all active write and read sessions
     * are completed before initiating the write session.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> writeSession(block: suspend AIAgentLLMWriteSession.() -> T): T

    /**
     * Executes a read session within the [AIAgentLLMContext], ensuring concurrent safety
     * with active write session and other read sessions.
     */
    @OptIn(ExperimentalStdlibApi::class)
    public suspend fun <T> readSession(block: suspend AIAgentLLMReadSession.() -> T): T

    /**
     * Returns the current prompt used in the LLM context.
     *
     * @return The current [Prompt] instance.
     */
    public fun copy(
        tools: List<ToolDescriptor> = this.tools,
        prompt: Prompt = this.prompt,
        model: LLModel = this.model,
        responseProcessor: ResponseProcessor? = this.responseProcessor,
        promptExecutor: PromptExecutor = this.promptExecutor,
        environment: AIAgentEnvironment = this.environment,
        config: AIAgentConfig = this.config,
        clock: Clock = this.clock
    ): AIAgentLLMContext
}
