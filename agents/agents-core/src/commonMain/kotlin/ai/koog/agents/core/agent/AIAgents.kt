package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.Clock
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi

/**
 * Convenience builder that creates an instance of [AIAgent], automatically deducing [GraphAIAgent.inputType] and [GraphAIAgent.outputType]
 * from [Input] and [Output]
 *
 * @property promptExecutor Executor used to manage and execute prompt strings.
 * @property strategy Strategy defining the local behavior of the agent.
 * @property agentConfig Configuration details for the local agent that define its operational parameters.
 * @property toolRegistry Registry of tools the agent can interact with, defaulting to an empty registry.
 * @property installFeatures Lambda for installing additional features within the agent environment.
 * @property clock The clock used to calculate message timestamps
 *
 * @see [AIAgent] class
 */
@OptIn(ExperimentalUuidApi::class)
public inline fun <reified Input, reified Output> AIAgent(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    strategy: AIAgentGraphStrategy<Input, Output>,
    id: String? = null, // If null, ID will be initialized as a random UUID lazily
    clock: Clock = Clock.System,
    noinline installFeatures: FeatureContext.() -> Unit = {},
): AIAgent<Input, Output> = GraphAIAgent(
    inputType = typeOf<Input>(),
    outputType = typeOf<Output>(),
    promptExecutor = promptExecutor,
    strategy = strategy,
    agentConfig = agentConfig,
    id = id,
    toolRegistry = toolRegistry,
    clock = clock,
    installFeatures = installFeatures,
)

/**
 * Convenience builder that creates an instance of an [AIAgent] with string input and output and the specified parameters.
 *
 * @param promptExecutor The [PromptExecutor] responsible for executing prompts.
 * @param strategy The [AIAgentGraphStrategy] defining the agent's behavior. Default is a single-run strategy.
 * @param systemPrompt The system-level prompt context for the agent. Default is an empty string.
 * @param llmModel The language model to be used by the agent.
 * @param temperature The sampling temperature for the language model, controlling randomness. Default is 1.0.
 * @param toolRegistry The [ToolRegistry] containing tools available to the agent. Default is an empty registry.
 * @param maxIterations Maximum number of iterations for the agent's execution. Default is 50.
 * @param installFeatures A suspending lambda to install additional features for the agent's functionality. Default is an empty lambda.
 *
 * @see [AIAgent] class
 */
@OptIn(ExperimentalUuidApi::class)
public fun AIAgent(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    strategy: AIAgentGraphStrategy<String, String> = singleRunStrategy(),
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null, // If null, ID will be initialized as a random UUID lazily
    systemPrompt: String = "",
    temperature: Double = 1.0,
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
            system(systemPrompt)
        },
        model = llmModel,
        maxAgentIterations = maxIterations,
    ),
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)
