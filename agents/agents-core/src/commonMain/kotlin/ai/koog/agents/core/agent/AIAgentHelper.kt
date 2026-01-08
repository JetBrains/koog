package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentState.Finished
import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.datetime.Clock
import kotlin.jvm.JvmStatic
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

@PublishedApi
internal object AIAgentHelper {
    suspend fun <Output> result(agent: AIAgent<*, Output>): Output = when (val state = agent.getState()) {
        is Finished<Output> -> state.result
        else -> throw IllegalStateException("Output is not ready, agent's state is: $state")
    }

    /**
     * Creates and returns a new instance of the `Builder` class to configure and construct an AI agent.
     *
     * @return An instance of `Builder` for configuring an AI agent.
     */
    @JvmStatic
    public fun builder(): AIAgentBuilder = AIAgentBuilder()

    /**
     * Creates an instance of an AI agent based on the provided configuration, input/output types,
     * and execution strategy.
     *
     * @param Input The type of the input the AI agent will process.
     * @param Output The type of the output the AI agent will produce.
     * @param promptExecutor The executor responsible for processing prompts and interacting with the language model.
     * @param agentConfig The configuration for the AI agent, including the prompt, model, and other parameters.
     * @param toolRegistry The registry of tools available for use by the agent. Defaults to an empty registry.
     * @param strategy The strategy for executing the AI agent's graph logic, including workflows and decision-making.
     * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
     * @param clock The clock to be used for time-related operations. Defaults to the system clock.
     * @param installFeatures A lambda expression to install additional features in the agent's feature context. Defaults to an empty implementation.
     * @return An instance of an AI agent configured with the specified parameters and capable of executing its logic.
     */
    @OptIn(ExperimentalUuidApi::class)
    @PublishedApi
    internal inline operator fun <reified Input, reified Output> invoke(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<Input, Output>,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        id: String? = null,
        clock: Clock = Clock.System,
        noinline installFeatures: FeatureContext.() -> Unit = {},
    ): AIAgent<Input, Output> {
        return GraphAIAgent(
            inputType = typeOf<Input>(),
            outputType = typeOf<Output>(),
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            strategy = strategy,
            id = id,
            clock = clock,
            installFeatures = installFeatures
        )
    }

    /**
     * Operator function to create and invoke an AI agent with the given parameters.
     *
     * @param promptExecutor The executor responsible for running the prompt and generating outputs.
     * @param prompt The prompt to be processed by the AI agent.
     * @param agentConfig Configuration settings for the AI agent.
     * @param strategy The strategy to be used for the AI agent's execution graph. Defaults to a single-run strategy.
     * @param toolRegistry Registry of tools available for the AI agent to use. Defaults to an empty registry.
     * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
     * @param installFeatures Lambda function for installing additional features into the feature context. Defaults to an empty lambda.
     * @return An instance of AIAgent configured with the graph strategy.
     */
    @OptIn(ExperimentalUuidApi::class)
    internal operator fun invoke(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        id: String? = null,
        installFeatures: FeatureContext.() -> Unit = {},
    ): GraphAIAgent<String, String> = GraphAIAgent(
        inputType = typeOf<String>(),
        outputType = typeOf<String>(),
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        strategy = strategy,
        id = id,
        clock = Clock.System,
        installFeatures = installFeatures
    )

    /**
     * Creates a functional AI agent with the provided configurations and execution strategy.
     *
     * @param Input The type of the input the AI agent will process.
     * @param Output The type of the output the AI agent will produce.
     * @param promptExecutor The executor responsible for running prompts against the language model.
     * @param agentConfig The configuration for the AI agent, including prompt setup, language model, and iteration limits.
     * @param toolRegistry The registry containing available tools for the AI agent. Defaults to an empty registry.
     * @param strategy The strategy for executing the agent's logic, including workflows and decision-making.
     * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
     * @return A `FunctionalAIAgent` instance configured with the provided parameters and execution strategy.
     */
    @OptIn(ExperimentalUuidApi::class)
    internal operator fun <Input, Output> invoke(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentFunctionalStrategy<Input, Output>,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        id: String? = null,
        clock: Clock = Clock.System,
        installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
    ): FunctionalAIAgent<Input, Output> {
        return FunctionalAIAgent(
            id = id,
            promptExecutor = promptExecutor,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
            strategy = strategy,
            clock = clock,
            installFeatures = installFeatures
        )
    }

    /**
     * Construction of an AI agent with the specified configurations and parameters.
     *
     * @param promptExecutor The executor responsible for processing language model prompts.
     * @param llmModel The specific large language model to be used for the agent.
     * @param strategy The strategy that defines the agent's workflow, defaulting to the [singleRunStrategy].
     * @param toolRegistry The set of tools available for the agent, defaulting to an empty registry.
     * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
     * @param systemPrompt The system-level prompt used as context for the agent, defaulting to an empty string.
     * @param temperature The randomness or creativity of the model's responses, with valid values ranging typically from 0.0 to 1.0. Defaults to 1.0.
     * @param numberOfChoices The number of response choices to be generated, defaulting to 1.
     * @param maxIterations The maximum number of iterations the agent is allowed to perform, defaulting to 50.
     * @param installFeatures A function to configure additional features into the agent during initialization. Defaults to an empty configuration.
     * @return An instance of [AIAgent] configured with the provided parameters.
     */
    @OptIn(ExperimentalUuidApi::class)
    internal operator fun invoke(
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
    ): AIAgent<String, String> = AIAgent(
        id = id,
        promptExecutor = promptExecutor,
        strategy = strategy,
        agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "chat",
                params = LLMParams(
                    temperature = temperature,
                    numberOfChoices = numberOfChoices
                )
            ) {
                systemPrompt?.let { system(it) }
            },
            model = llmModel,
            maxAgentIterations = maxIterations,
            responseProcessor = responseProcessor
        ),
        toolRegistry = toolRegistry,
        installFeatures = installFeatures
    )

    /**
     * Creates and configures an AI agent using the provided parameters.
     *
     * @param Input The input type for the AI agent.
     * @param Output The output type for the AI agent.
     * @param promptExecutor An instance of [PromptExecutor] responsible for executing prompts with the language model.
     * @param llmModel The language model [LLModel] to be used by the agent.
     * @param strategy The agent strategy [AIAgentGraphStrategy] defining how the agent processes inputs and outputs.
     * @param toolRegistry An optional [ToolRegistry] specifying the tools available to the agent for execution. Defaults to `[ToolRegistry.EMPTY]`.
     * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
     * @param clock A `Clock` instance used for time-related operations. Defaults to `Clock.System`.
     * @param systemPrompt A string representing the system-level prompt for the agent. Defaults to an empty string.
     * @param temperature A double value controlling the randomness of the model's output. Defaults to `1.0`.
     * @param numberOfChoices The number of choices the model should generate per invocation. Defaults to `1`.
     * @param maxIterations The maximum number of iterations the agent can perform. Defaults to `50`.
     * @param installFeatures An extension function on `FeatureContext` to install custom features for the agent. Defaults to an empty lambda.
     * @return A configured [AIAgent] instance that can process inputs and generate outputs using the specified strategy and model.
     */
    @OptIn(ExperimentalUuidApi::class)
    @PublishedApi
    internal inline operator fun <reified Input, reified Output> invoke(
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
    ): AIAgent<Input, Output> {
        return AIAgent(
            id = id,
            promptExecutor = promptExecutor,
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    id = "chat",
                    params = LLMParams(
                        temperature = temperature,
                        numberOfChoices = numberOfChoices
                    )
                ) {
                    systemPrompt?.let { system(it) }
                },
                model = llmModel,
                maxAgentIterations = maxIterations,
                responseProcessor = responseProcessor
            ),
            toolRegistry = toolRegistry,
            installFeatures = installFeatures
        )
    }

    /**
     * Creates an [FunctionalAIAgent] with the specified parameters to execute a strategy with the assistance of a tool registry,
     * configured language model, and associated features.
     *
     * @param promptExecutor The executor used to process prompts for the language model.
     * @param llmModel The language model configuration defining the underlying LLM instance and its behavior.
     * @param func The operational strategy for the AI agent, which determines how to handle the provided input.
     * @param toolRegistry Registry containing tools available to the agent for use during execution. Default is an empty registry.
     * @param id Unique identifier for the agent. Random UUID will be generated if set to null.
     * @param systemPrompt The system prompt that sets the initial context or instructions for the AI agent.
     * @param temperature The temperature setting for the language model, which adjusts the diversity of output. Default is 1.0.
     * @param numberOfChoices The number of response choices to generate when querying the language model. Default is 1.
     * @param maxIterations The maximum number of iterations the agent is allowed to perform during execution. Default is 50.
     * @param installFeatures A lambda to configure and install features in the agent's context.
     * @return An AI agent instance configured with the provided parameters and ready to execute the specified strategy.
     */
    internal operator fun <Input, Output> invoke(
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
    ): AIAgent<Input, Output> = FunctionalAIAgent(
        promptExecutor = promptExecutor,
        agentConfig = AIAgentConfig(
            prompt = prompt(
                id = "chat",
                params = LLMParams(
                    temperature = temperature,
                    numberOfChoices = numberOfChoices
                )
            ) {
                systemPrompt?.let { system(it) }
            },
            model = llmModel,
            maxAgentIterations = maxIterations,
            responseProcessor = responseProcessor
        ),
        installFeatures = installFeatures,
        toolRegistry = toolRegistry,
        strategy = strategy
    )
}
