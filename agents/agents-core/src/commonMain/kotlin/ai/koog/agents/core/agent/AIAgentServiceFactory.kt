@file:Suppress("FunctionName")

package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlin.jvm.JvmSynthetic

/**
 * Factory functions for creating AIAgentService instances.
 */

/**
 * Creates a [GraphAIAgentService] instance with the provided configuration, strategy,
 * tool registry, and optional feature installation logic.
 *
 * @param Input The input type that the service processes.
 * @param Output The output type that the service produces.
 * @param promptExecutor The executor responsible for processing AI prompts and responses.
 * @param agentConfig Configuration parameters for the AI agent.
 * @param strategy A strategy defining the graph structure for AI agent interactions and processing.
 * @param toolRegistry The registry of tools available to the agent. Defaults to an empty registry.
 * @param installFeatures A lambda expression to install additional features in the agent's feature context.
 * @return A [GraphAIAgentService] instance configured with the provided parameters.
 */
@OptIn(InternalAgentsApi::class)
@JvmSynthetic
public fun <Input, Output> AIAgentService(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentGraphStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    installFeatures: FeatureContext.() -> Unit = {},
): GraphAIAgentService<Input, Output> = GraphAIAgentService(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = strategy,
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)

/**
 * Creates a [FunctionalAIAgentService] instance with the provided parameters.
 *
 * @param Input The type of input data expected by the service.
 * @param Output The type of output data produced by the service.
 * @param promptExecutor The executor responsible for handling prompts and managing their execution.
 * @param agentConfig The configuration parameters for the AI agent.
 * @param strategy The functional strategy that defines the behavior and capabilities of the AI agent.
 * @param toolRegistry The registry containing tools that can be used by the agent. Defaults to an empty registry.
 * @param installFeatures A lambda expression to configure and install additional features to the AI agent context.
 * @return A [FunctionalAIAgentService] instance initialized with the given parameters.
 */
@OptIn(InternalAgentsApi::class)
@JvmSynthetic
public fun <Input, Output> AIAgentService(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    strategy: AIAgentFunctionalStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    installFeatures: FunctionalAIAgent.FeatureContext.() -> Unit = {},
): FunctionalAIAgentService<Input, Output> = FunctionalAIAgentService(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = strategy,
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)

/**
 * Invokes the process to create and return an instance of `GraphAIAgentService`.
 *
 * @param promptExecutor The executor responsible for handling prompts during the agent's operation.
 * @param agentConfig The configuration object for the AI agent.
 * @param toolRegistry The registry containing tools available for the agent to use. Defaults to an empty tool registry.
 * @param installFeatures A lambda function to install additional features into the agent's feature context.
 */
@OptIn(InternalAgentsApi::class)
@JvmSynthetic
public fun AIAgentService(
    promptExecutor: PromptExecutor,
    agentConfig: AIAgentConfig,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    installFeatures: FeatureContext.() -> Unit = {},
): GraphAIAgentService<String, String> = AIAgentService(
    promptExecutor = promptExecutor,
    agentConfig = agentConfig,
    strategy = singleRunStrategy(),
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)

/**
 * Creates a [GraphAIAgentService] with the default [singleRunStrategy] and the given model parameters.
 *
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param llmModel Language model to use.
 * @param strategy Graph strategy defining the agent's workflow.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param systemPrompt Optional system prompt for the agent.
 * @param temperature Optional sampling temperature for the model, typically between 0.0 and 1.0.
 * @param maxIterations Maximum number of agent iterations. Defaults to 50.
 * @param responseProcessor Optional processor for the model's responses.
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [GraphAIAgentService] instance configured with the provided parameters.
 */
@OptIn(InternalAgentsApi::class)
@JvmSynthetic
public fun <Input, Output> AIAgentService(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    strategy: AIAgentGraphStrategy<Input, Output>,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    systemPrompt: String? = null,
    temperature: Double? = null,
    maxIterations: Int = 50,
    responseProcessor: ResponseProcessor? = null,
    installFeatures: FeatureContext.() -> Unit = {}
): GraphAIAgentService<Input, Output> = AIAgentService(
    promptExecutor = promptExecutor,
    agentConfig = createAgentConfig(llmModel, systemPrompt, temperature, maxIterations, responseProcessor),
    strategy = strategy,
    toolRegistry = toolRegistry,
    installFeatures = installFeatures
)

/**
 * Creates a [GraphAIAgentService] with the default [singleRunStrategy] and the given model parameters.
 *
 * @param promptExecutor Executor responsible for processing prompts and interacting with the language model.
 * @param llmModel Language model to use.
 * @param toolRegistry Registry of tools available to the agent. Defaults to an empty registry.
 * @param systemPrompt Optional system prompt for the agent.
 * @param temperature Optional sampling temperature for the model, typically between 0.0 and 1.0.
 * @param maxIterations Maximum number of agent iterations. Defaults to 50.
 * @param responseProcessor Optional processor for the model's responses.
 * @param installFeatures Lambda to install additional features into the agent's feature context.
 * @return A [GraphAIAgentService] instance configured with the provided parameters.
 */
@OptIn(InternalAgentsApi::class)
@JvmSynthetic
public fun AIAgentService(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    toolRegistry: ToolRegistry = ToolRegistry.EMPTY,
    systemPrompt: String? = null,
    temperature: Double? = null,
    maxIterations: Int = 50,
    responseProcessor: ResponseProcessor? = null,
    installFeatures: FeatureContext.() -> Unit = {}
): GraphAIAgentService<String, String> = AIAgentService(
    promptExecutor = promptExecutor,
    llmModel = llmModel,
    strategy = singleRunStrategy(),
    toolRegistry = toolRegistry,
    systemPrompt = systemPrompt,
    temperature = temperature,
    maxIterations = maxIterations,
    responseProcessor = responseProcessor,
    installFeatures = installFeatures
)
