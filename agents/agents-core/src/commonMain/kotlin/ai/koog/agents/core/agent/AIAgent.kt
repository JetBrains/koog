@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentRunSession
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.utils.io.Closeable
import kotlinx.datetime.Clock
import kotlin.jvm.JvmStatic
import kotlin.uuid.ExperimentalUuidApi

/**
 * Represents a basic interface for AI agent.
 */
public expect abstract class AIAgent<Input, Output> constructor() : Closeable {

    /**
     * Represents the unique identifier for the AI agent.
     */
    public abstract val id: String

    /**
     * The configuration for the AI agent.
     */
    public abstract val agentConfig: AIAgentConfig

    /**
     * Executes the AI agent with the given input and retrieves the resulting output.
     *
     * @param agentInput The input for the agent.
     * @return The output produced by the agent.
     */
    public abstract suspend fun run(agentInput: Input, sessionId: String? = null): Output

    /**
     * Creates a new session for executing the agent with the given input.
     *
     * This method provides a way to get a session object that can be used to execute
     * the agent independently. The session manages the complete execution lifecycle, including
     * state tracking, pipeline coordination, and strategy execution.
     *
     * @return A session instance that can be used to run the agent with specific input and context.
     */
    public abstract fun createSession(sessionId: String? = null): AIAgentRunSession<Input, Output, out AIAgentContext>

    /**
     * The companion object for the AIAgent class, providing functionality to instantiate an AI agent
     * with a flexible configuration, input/output types, and execution strategy.
     */
    public companion object {
        /**
         * Creates and returns a new instance of the [AIAgentBuilder] class to configure and construct an AI agent.
         *
         * @return An instance of `Builder` for configuring an AI agent.
         */
        @JvmStatic
        public fun builder(): AIAgentBuilder

        /**
         * Creates an instance of an AI agent based on the provided configuration, input/output types,
         * and execution strategy.
         *
         * @param Input The type of the input the AI agent will process.
         * @param Output The type of the output the AI agent will produce.
         * @param promptExecutor The executor responsible for processing prompts and interacting with the language model.
         * @param agentConfig The configuration for the AI agent, including the prompt, model, and other parameters.
         * @param strategy The strategy for executing the AI agent's graph logic, including workflows and decision-making.
         * @param toolRegistry The registry of tools available for use by the agent. Defaults to an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param clock The clock to be used for time-related operations. Defaults to the system clock.
         * @param installFeatures A lambda expression to install additional features in the agent's feature context. Defaults to an empty implementation.
         * @return An instance of an AI agent configured with the specified parameters and capable of executing its logic.
         */
        @OptIn(ExperimentalUuidApi::class)
        public inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<Input, Output>,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            clock: Clock = Clock.System,
            noinline installFeatures: FeatureContext.() -> Unit = {},
        ): AIAgent<Input, Output>

        /**
         * Operator function to create and invoke an AI agent with the given parameters.
         *
         * @param promptExecutor The executor responsible for running the prompt and generating outputs.
         * @param agentConfig Configuration settings for the AI agent.
         * @param strategy The strategy to be used for the AI agent's execution graph. Defaults to a single-run strategy.
         * @param toolRegistry Registry of tools available for the AI agent to use. Defaults to an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param installFeatures Lambda function for installing additional features into the feature context. Defaults to an empty lambda.
         * @return An instance of AIAgent configured with the graph strategy.
         */
        @OptIn(ExperimentalUuidApi::class)
        public operator fun invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            installFeatures: FeatureContext.() -> Unit = {},
        ): GraphAIAgent<String, String>

        /**
         * Creates a functional AI agent with the provided configurations and execution strategy.
         *
         * @param Input The type of the input the AI agent will process.
         * @param Output The type of the output the AI agent will produce.
         * @param promptExecutor The executor responsible for running prompts against the language model.
         * @param agentConfig The configuration for the AI agent, including prompt setup, language model, and iteration limits.
         * @param strategy The strategy for executing the agent's logic, including workflows and decision-making.
         * @param toolRegistry The registry containing available tools for the AI agent. Defaults to an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param clock The clock instance used for time-related operations. Defaults to the system clock.
         * @param installFeatures A lambda expression to install additional features in the agent's feature context. Defaults to an empty implementation.
         * @return A `FunctionalAIAgent` instance configured with the provided parameters and execution strategy.
         */
        @OptIn(ExperimentalUuidApi::class)
        public operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            agentConfig: AIAgentConfig,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            clock: Clock = Clock.System,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
        ): FunctionalAIAgent<Input, Output>

        /**
         * Construction of an AI agent with the specified configurations and parameters.
         *
         * @param promptExecutor The executor responsible for processing language model prompts.
         * @param llmModel The specific large language model to be used for the agent.
         * @param responseProcessor The processor responsible for processing the model's responses.
         * @param strategy The strategy that defines the agent's workflow, defaulting to the [singleRunStrategy].
         * @param toolRegistry The set of tools available for the agent, defaulting to an empty registry.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param systemPrompt Optional system prompt for the agent.
         * @param temperature Optional model temperature, with valid values ranging typically from 0.0 to 1.0.
         * @param numberOfChoices The number of response choices to be generated, defaulting to 1.
         * @param maxIterations The maximum number of iterations the agent is allowed to perform, defaulting to 50.
         * @param installFeatures A function to configure additional features into the agent during initialization. Defaults to an empty configuration.
         * @return An instance of [AIAgent] configured with the provided parameters.
         */
        @OptIn(ExperimentalUuidApi::class)
        public operator fun invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            responseProcessor: ResponseProcessor? = null,
            strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            systemPrompt: String? = null,
            temperature: Double? = null,
            numberOfChoices: Int = 1,
            maxIterations: Int = 50,
            installFeatures: FeatureContext.() -> Unit = {}
        ): AIAgent<String, String>

        /**
         * Creates and configures an AI agent using the provided parameters.
         *
         * @param Input The input type for the AI agent.
         * @param Output The output type for the AI agent.
         * @param promptExecutor An instance of [PromptExecutor] responsible for executing prompts with the language model.
         * @param llmModel The language model [LLModel] to be used by the agent.
         * @param strategy The agent strategy [AIAgentGraphStrategy] defining how the agent processes inputs and outputs.
         * @param responseProcessor The processor responsible for processing the model's responses.
         * @param toolRegistry An optional [ToolRegistry] specifying the tools available to the agent for execution. Defaults to `[ToolRegistry.EMPTY]`.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param clock A `Clock` instance used for time-related operations. Defaults to `Clock.System`.
         * @param systemPrompt Optional system prompt for the agent.
         * @param temperature Optional model temperature, with valid values ranging typically from 0.0 to 1.0.
         * @param numberOfChoices The number of choices the model should generate per invocation. Defaults to `1`.
         * @param maxIterations The maximum number of iterations the agent can perform. Defaults to `50`.
         * @param installFeatures An extension function on `FeatureContext` to install custom features for the agent. Defaults to an empty lambda.
         * @return A configured [AIAgent] instance that can process inputs and generate outputs using the specified strategy and model.
         */
        @OptIn(ExperimentalUuidApi::class)
        public inline operator fun <reified Input, reified Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            strategy: AIAgentGraphStrategy<Input, Output>,
            responseProcessor: ResponseProcessor? = null,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            id: String? = null,
            clock: Clock = Clock.System,
            systemPrompt: String? = null,
            temperature: Double? = null,
            numberOfChoices: Int = 1,
            maxIterations: Int = 50,
            noinline installFeatures: FeatureContext.() -> Unit = {},
        ): AIAgent<Input, Output>

        /**
         * Creates an [FunctionalAIAgent] with the specified parameters to execute a strategy with the assistance of a tool registry,
         * configured language model, and associated features.
         *
         * @param Input The type of input accepted by the agent.
         * @param Output The type of output produced by the agent.
         * @param promptExecutor The executor used to process prompts for the language model.
         * @param llmModel The language model configuration defining the underlying LLM instance and its behavior.
         * @param responseProcessor The processor responsible for processing the model's responses.
         * @param toolRegistry Registry containing tools available to the agent for use during execution. Default is an empty registry.
         * @param strategy The strategy to be executed by the agent. Default is a single-run strategy.
         * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
         * @param systemPrompt Optional system prompt for the agent.
         * @param temperature Optional model temperature, with valid values ranging typically from 0.0 to 1.0.
         * @param numberOfChoices The number of response choices to generate when querying the language model. Default is 1.
         * @param maxIterations The maximum number of iterations the agent is allowed to perform during execution. Default is 50.
         * @param installFeatures A lambda to configure and install features in the agent's context.
         * @return An AI agent instance configured with the provided parameters and ready to execute the specified strategy.
         */
        public operator fun <Input, Output> invoke(
            promptExecutor: PromptExecutor,
            llmModel: LLModel,
            responseProcessor: ResponseProcessor? = null,
            toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
            strategy: AIAgentFunctionalStrategy<Input, Output>,
            id: String? = null,
            systemPrompt: String? = null,
            temperature: Double? = null,
            numberOfChoices: Int = 1,
            maxIterations: Int = 50,
            installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
        ): AIAgent<Input, Output>
    }
}
