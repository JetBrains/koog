@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlin.reflect.typeOf

@PublishedApi
internal object AIAgentServiceHelper {
    /**
     * Converts a given [GraphAIAgent] instance into an [AIAgentService] instance.
     *
     * @param Input The input type that the agent processes.
     * @param Output The output type that the agent produces.
     * @param agent The [GraphAIAgent] to be converted into a service instance.
     * @return An [AIAgentService] instance constructed from the provided [GraphAIAgent].
     */
    @OptIn(InternalAgentsApi::class)
    @PublishedApi
    internal inline fun <reified Input, reified Output> fromAgent(
        agent: GraphAIAgent<Input, Output>
    ): AIAgentService<Input, Output, GraphAIAgent<Input, Output>> =
        AIAgentService(
            promptExecutor = agent.promptExecutor,
            agentConfig = agent.agentConfig,
            strategy = agent.strategy,
            toolRegistry = agent.toolRegistry,
            installFeatures = agent.installFeatures
        )

    /**
     * Creates a new instance of [AIAgentService] by transforming a given [FunctionalAIAgent].
     *
     * @param Input The type of input data expected by the agent.
     * @param Output The type of output data produced by the agent.
     * @param agent The [FunctionalAIAgent] to be transformed into an [AIAgentService].
     * @return A new [AIAgentService] instance configured with the parameters of the provided agent.
     */
    @OptIn(InternalAgentsApi::class)
    internal fun <Input, Output> fromAgent(
        agent: FunctionalAIAgent<Input, Output>
    ): AIAgentService<Input, Output, FunctionalAIAgent<Input, Output>> =
        AIAgentService(
            promptExecutor = agent.promptExecutor,
            agentConfig = agent.agentConfig,
            strategy = agent.strategy,
            toolRegistry = agent.toolRegistry,
            installFeatures = agent.installFeatures
        )

    /**
     * Invokes the creation of a [GraphAIAgentService] instance with the provided configuration, strategy,
     * tool registry, and optional feature installation logic.
     *
     * @param promptExecutor The executor responsible for processing AI prompts and responses.
     * @param agentConfig Configuration parameters for the AI agent.
     * @param strategy A strategy defining the graph structure for AI agent interactions and processing.
     * @param toolRegistry*/
    @OptIn(InternalAgentsApi::class)
    @PublishedApi
    internal inline operator fun <reified Input, reified Output> invoke(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentGraphStrategy<Input, Output>,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        noinline installFeatures: FeatureContext.() -> Unit = {},
    ): GraphAIAgentService<Input, Output> = GraphAIAgentService(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = strategy,
        inputType = typeOf<Input>(),
        outputType = typeOf<Output>(),
        toolRegistry = toolRegistry,
        installFeatures = installFeatures
    )

    /**
     * Invokes the creation of a [GraphAIAgentService] with the provided dependencies, configuration,
     * and optional parameters for customization.
     *
     * @param promptExecutor The executor responsible for handling prompt-based interactions.
     * @param llmModel The large language model to be used by the agent.
     * @param strategy The graph strategy defining the agent's execution behavior. Defaults to a single-run*/
    internal operator fun invoke(
        promptExecutor: PromptExecutor,
        llmModel: LLModel,
        strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        systemPrompt: String? = null,
        temperature: Double? = null,
        numberOfChoices: Int = 1,
        maxIterations: Int = 50,
        installFeatures: FeatureContext.() -> Unit = {}
    ): GraphAIAgentService<String, String> = AIAgentService(
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
        ),
        toolRegistry = toolRegistry,
        installFeatures = installFeatures
    )

    /**
     * Invokes the creation of a FunctionalAIAgentService instance with the provided parameters.
     *
     * @param promptExecutor The executor responsible for handling prompts and managing their execution.
     * @param agentConfig The configuration parameters for the AI agent.
     * @param strategy The functional strategy that defines the behavior and capabilities of the AI agent.
     * @param toolRegistry The registry containing tools that can be used by the agent. Defaults to an empty registry if not specified.
     * @param installFeatures A lambda expression to configure and install additional features to the AI agent context.
     * @return An instance of FunctionalAIAgentService initialized with the given parameters.
     */
    @OptIn(InternalAgentsApi::class)
    internal operator fun <Input, Output> invoke(
        promptExecutor: PromptExecutor,
        agentConfig: AIAgentConfig,
        strategy: AIAgentFunctionalStrategy<Input, Output>,
        toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
        installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
    ): FunctionalAIAgentService<Input, Output> = FunctionalAIAgentService(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
        strategy = strategy,
        installFeatures = installFeatures
    )
}
