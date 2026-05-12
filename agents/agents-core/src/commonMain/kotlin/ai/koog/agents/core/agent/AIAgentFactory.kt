@file:Suppress("FunctionName")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.PlannerAIAgent
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.utils.time.KoogClock
import kotlin.jvm.JvmSynthetic

/**
 * Factory functions for creating AIAgent instances.
 */

// region: factory methods using AIAgentConfig

/**
 * Creates a [GraphAIAgent] with the given graph strategy and configuration.
 *
 * @param Input The type of input the agent processes.
 * @param Output The type of output the agent produces.
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param agentConfig Configuration for the agent, including the prompt, model, and other parameters.
 * @param strategy Graph strategy defining the agent's workflow.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [GraphAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun <Input, Output> AIAgent(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentGraphStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: GraphAIAgent.FeatureContext.() -> Unit = {},
): GraphAIAgent<Input, Output> = GraphAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = strategy,
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

/**
 * Creates a [GraphAIAgent] with the default [singleRunStrategy] and the given configuration.
 *
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param agentConfig Configuration for the agent, including the prompt, model, and other parameters.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [GraphAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun AIAgent(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: GraphAIAgent.FeatureContext.() -> Unit = {},
): GraphAIAgent<String, String> = AIAgent(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = singleRunStrategy(),
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

/**
 * Creates a [FunctionalAIAgent] with the given functional strategy and configuration.
 *
 * @param Input The type of input the agent processes.
 * @param Output The type of output the agent produces.
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param agentConfig Configuration for the agent, including the prompt, model, and other parameters.
 * @param strategy Functional strategy defining the agent's execution logic.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [FunctionalAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun <Input, Output> AIAgent(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentFunctionalStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
): FunctionalAIAgent<Input, Output> = FunctionalAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = strategy,
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

/**
 * Creates a [PlannerAIAgent] with the given planner strategy and configuration.
 *
 * @param Input The type of input the agent processes.
 * @param Output The type of output the agent produces.
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param agentConfig Configuration for the agent, including the prompt, model, and other parameters.
 * @param strategy Planner strategy defining the agent's workflow.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [PlannerAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun <Input, Output> AIAgent(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentPlannerStrategy<Input, Output, *>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: PlannerAIAgent.FeatureContext.() -> Unit = {},
): PlannerAIAgent<Input, Output> = PlannerAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = strategy,
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

// endregion: factory methods using AIAgentConfig

// region: factory methods using llModel, systemPrompt, temperature, etc.

/**
 * Creates a [GraphAIAgent] with the given graph strategy and model parameters.
 *
 * @param Input The type of input the agent processes.
 * @param Output The type of output the agent produces.
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param llmModel Language model to use.
 * @param strategy Graph strategy defining the agent's workflow.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param systemPrompt Optional system prompt for the agent.
 * @param temperature Optional sampling temperature for the model, typically between 0.0 and 1.0.
 * @param maxIterations Maximum number of agent iterations. Defaults to 50.
 * @param responseProcessor Optional processor for the model's responses.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [GraphAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun <Input, Output> AIAgent(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    strategy: AIAgentGraphStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    systemPrompt: String? = null,
    temperature: Double? = null,
    maxIterations: Int = 50,
    responseProcessor: ResponseProcessor? = null,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: GraphAIAgent.FeatureContext.() -> Unit = {},
): GraphAIAgent<Input, Output> = GraphAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = createAgentConfig(llmModel, systemPrompt, temperature, maxIterations, responseProcessor),
    strategy = strategy,
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

/**
 * Creates a [GraphAIAgent] with the default [singleRunStrategy] and the given model parameters.
 *
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param llmModel Language model to use.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param systemPrompt Optional system prompt for the agent.
 * @param temperature Optional sampling temperature for the model, typically between 0.0 and 1.0.
 * @param maxIterations Maximum number of agent iterations. Defaults to 50.
 * @param responseProcessor Optional processor for the model's responses.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [GraphAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun AIAgent(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    systemPrompt: String? = null,
    temperature: Double? = null,
    maxIterations: Int = 50,
    responseProcessor: ResponseProcessor? = null,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: GraphAIAgent.FeatureContext.() -> Unit = {}
): GraphAIAgent<String, String> = GraphAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = createAgentConfig(llmModel, systemPrompt, temperature, maxIterations, responseProcessor),
    strategy = singleRunStrategy(),
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

/**
 * Creates a [FunctionalAIAgent] with the given functional strategy and model parameters.
 *
 * @param Input The type of input the agent processes.
 * @param Output The type of output the agent produces.
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param llmModel Language model to use.
 * @param strategy Functional strategy defining the agent's execution logic.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param systemPrompt Optional system prompt for the agent.
 * @param temperature Optional sampling temperature for the model, typically between 0.0 and 1.0.
 * @param maxIterations Maximum number of agent iterations. Defaults to 50.
 * @param responseProcessor Optional processor for the model's responses.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [FunctionalAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun <Input, Output> AIAgent(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    strategy: AIAgentFunctionalStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    systemPrompt: String? = null,
    temperature: Double? = null,
    maxIterations: Int = 50,
    responseProcessor: ResponseProcessor? = null,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
): FunctionalAIAgent<Input, Output> = FunctionalAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = createAgentConfig(llmModel, systemPrompt, temperature, maxIterations, responseProcessor),
    strategy = strategy,
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

/**
 * Creates a [PlannerAIAgent] with the given planner strategy and model parameters.
 *
 * @param Input The type of input the agent processes.
 * @param Output The type of output the agent produces.
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param llmModel Language model to use.
 * @param strategy Planner strategy defining the agent's workflow.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param systemPrompt Optional system prompt for the agent.
 * @param temperature Optional sampling temperature for the model, typically between 0.0 and 1.0.
 * @param maxIterations Maximum number of agent iterations. Defaults to 50.
 * @param responseProcessor Optional processor for the model's responses.
 * @param id Unique identifier for the agent. A random UUID is used if null.
 * @param clock Clock for time-related operations. Defaults to [KoogClock.System].
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [PlannerAIAgent] instance configured with the provided parameters.
 */
@JvmSynthetic
public fun <Input, Output> AIAgent(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    strategy: AIAgentPlannerStrategy<Input, Output, *>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    systemPrompt: String? = null,
    temperature: Double? = null,
    maxIterations: Int = 50,
    responseProcessor: ResponseProcessor? = null,
    id: String? = null,
    clock: KoogClock = KoogClock.System,
    installFeatures: PlannerAIAgent.FeatureContext.() -> Unit = {},
): PlannerAIAgent<Input, Output> = PlannerAIAgent(
    promptExecutor = promptExecutor,
    agentConfig = createAgentConfig(llmModel, systemPrompt, temperature, maxIterations, responseProcessor),
    strategy = strategy,
    toolRegistry = toolRegistry,
    id = id,
    clock = clock,
    installFeatures = installFeatures
)

internal fun createAgentConfig(
    llModel: LLModel,
    systemPrompt: String?,
    temperature: Double?,
    maxIterations: Int,
    responseProcessor: ResponseProcessor?
): AIAgentConfig = AIAgentConfig(
    prompt = prompt(
        id = "chat",
        params = LLMParams(temperature = temperature)
    ) {
        systemPrompt?.let { system(it) }
    },
    model = llModel,
    maxAgentIterations = maxIterations,
    responseProcessor = responseProcessor
)
