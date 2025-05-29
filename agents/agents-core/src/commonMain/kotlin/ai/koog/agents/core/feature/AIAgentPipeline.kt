package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.handler.*
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.features.common.config.FeatureConfig
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into stage contexts
 * - Intercept agent creation
 * - Intercept node execution before and after it happens
 * - Intercept LLM (Language Learning Model) calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 */
public class AIAgentPipeline {

    /**
     * Companion object for the AgentPipeline class.
     */
    private companion object {
        /**
         * Logger instance for the AgentPipeline class.
         */
        private val logger = KotlinLogging.logger {  }
    }

    private val featurePrepareDispatcher = Dispatchers.Default.limitedParallelism(5)

    /**
     * Map of registered features and their configurations.
     * Keys are feature storage keys, values are feature configurations.
     */
    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, FeatureConfig> = mutableMapOf()

    /**
     * Map of agent handlers registered for different features.
     * Keys are feature storage keys, values are agent handlers.
     */
    private val agentHandlers: MutableMap<AIAgentStorageKey<*>, AgentHandler<*>> = mutableMapOf()

    /**
     * Map of strategy handlers registered for different features.
     * Keys are feature storage keys, values are strategy handlers.
     */
    private val strategyHandlers: MutableMap<AIAgentStorageKey<*>, StrategyHandler<*>> = mutableMapOf()

    /**
     * Map of stage context handlers registered for different features.
     * Keys are feature storage keys, values are stage context handlers.
     */
    private val agentContextHandler: MutableMap<AIAgentStorageKey<*>, AgentContextHandler<*>> = mutableMapOf()

    /**
     * Map of node execution handlers registered for different features.
     * Keys are feature storage keys, values are node execution handlers.
     */
    private val executeNodeHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteNodeHandler> = mutableMapOf()

    /**
     * Map of tool execution handlers registered for different features.
     * Keys are feature storage keys, values are tool execution handlers.
     */
    private val executeToolHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteToolHandler> = mutableMapOf()

    /**
     * Map of LLM execution handlers registered for different features.
     * Keys are feature storage keys, values are LLM execution handlers.
     */
    private val executeLLMHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteLLMHandler> = mutableMapOf()

    /**
     * Installs a feature into the pipeline with the provided configuration.
     *
     * This method initializes the feature with a custom configuration and registers it in the pipeline.
     * The feature's message processors are initialized during installation.
     *
     * @param Config The type of the feature configuration
     * @param Feature The type of the feature being installed
     * @param feature The feature implementation to be installed
     * @param configure A lambda to customize the feature configuration
     */
    public fun <Config : FeatureConfig, Feature : Any> install(
        feature: AIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        val config = feature.createInitialConfig().apply { configure() }
        feature.install(
            config = config,
            pipeline = this,
        )

        registeredFeatures[feature.key] = config
    }

    internal suspend fun prepareFeatures() {
        withContext(featurePrepareDispatcher) {
            registeredFeatures.values.forEach { featureConfig ->
                featureConfig.messageProcessor.map { processor ->
                    launch {
                        logger.debug { "Start preparing processor: ${processor::class.simpleName}" }
                        processor.initialize()
                        logger.debug { "Finished preparing processor: ${processor::class.simpleName}" }
                    }
                }
            }
        }
    }

    /**
     * Closes all feature stream providers.
     *
     * This internal method properly shuts down all message processors of registered features,
     * ensuring resources are released appropriately.
     */
    internal suspend fun closeFeaturesStreamProviders() {
        registeredFeatures.values.forEach { config -> config.messageProcessor.forEach { provider -> provider.close() } }
    }

    //region Trigger Agent Handlers

    /**
     * Notifies all registered handlers that an agent has started execution.
     *
     * @param strategy The strategy being executed by the agent
     * @param agent The agent instance for which the execution has started
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onBeforeAgentStarted(strategy: AIAgentStrategy, agent: AIAgent) {
        agentHandlers.values.forEach { handler ->
            val context = AgentStartContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.handleBeforeAgentStartedUnsafe(context)
        }
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param strategyName The name of the strategy that was executed
     * @param result The result produced by the agent, or null if no result was produced
     */
    public suspend fun onAgentFinished(strategyName: String, result: String?) {
        agentHandlers.values.forEach { handler -> handler.agentFinishedHandler.handle(strategyName, result) }
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param strategyName The name of the strategy during which the error occurred
     * @param throwable The exception that was thrown during agent execution
     */
    public suspend fun onAgentRunError(strategyName: String, throwable: Throwable) {
        agentHandlers.values.forEach { handler -> handler.agentRunErrorHandler.handle(strategyName, throwable) }
    }

    /**
     * Transforms the agent environment by applying all registered environment transformers.
     *
     * This method allows features to modify or enhance the agent's environment before it starts execution.
     * Each registered handler can apply its own transformations to the environment in sequence.
     *
     * @param strategy The strategy associated with the agent
     * @param agent The agent instance for which the environment is being transformed
     * @param baseEnvironment The initial environment to be transformed
     * @return The transformed environment after all handlers have been applied
     */
    public fun transformEnvironment(
        strategy: AIAgentStrategy,
        agent: AIAgent,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        return agentHandlers.values.fold(baseEnvironment) { env, handler ->
            val context = AgentCreateContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.transformEnvironmentUnsafe(context, env)
        }
    }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param strategy The strategy that has started execution
     */
    public suspend fun onStrategyStarted(strategy: AIAgentStrategy) {
        strategyHandlers.values.forEach { handler ->
            val updateContext = StrategyUpdateContext(strategy, handler.feature)
            handler.handleStrategyStartedUnsafe(updateContext)
        }
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param strategyName The name of the strategy that has finished
     * @param result The result produced by the strategy execution
     */
    public suspend fun onStrategyFinished(strategyName: String, result: String) {
        strategyHandlers.values.forEach { handler -> handler.strategyFinishedHandler.handle(strategyName, result) }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger Stage Context Handlers

    /**
     * Retrieves all features associated with the given stage context.
     *
     * This method collects features from all registered stage context handlers
     * that are applicable to the provided context.
     *
     * @param context The stage context for which to retrieve features
     * @return A map of feature keys to their corresponding feature instances
     */
    public fun getAgentFeatures(context: AIAgentContextBase): Map<AIAgentStorageKey<*>, Any> {
        return agentContextHandler.mapValues { (_, featureProvider) ->
            featureProvider.handle(context)
        }
    }

    //endregion Trigger Stage Context Handlers

    //region Trigger Node Handlers

    /**
     * Notifies all registered node handlers before a node is executed.
     *
     * @param node The node that is about to be executed
     * @param context The stage context in which the node is being executed
     * @param input The input data for the node execution
     */
    public suspend fun onBeforeNode(node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) {
        executeNodeHandlers.values.forEach { handler -> handler.beforeNodeHandler.handle(node, context, input) }
    }

    /**
     * Notifies all registered node handlers after a node has been executed.
     *
     * @param node The node that was executed
     * @param context The stage context in which the node was executed
     * @param input The input data that was provided to the node
     * @param output The output data produced by the node execution
     */
    public suspend fun onAfterNode(
        node: AIAgentNodeBase<*, *>,
        context: AIAgentContextBase,
        input: Any?,
        output: Any?
    ) {
        executeNodeHandlers.values.forEach { handler -> handler.afterNodeHandler.handle(node, context, input, output) }
    }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param prompt The prompt that will be sent to the language model
     */
    public suspend fun onBeforeLLMCall(prompt: Prompt,  tools: List<ToolDescriptor>) {
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallHandler.handle(prompt, tools) }
    }

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param responses A single or multiple response messages received from the language model
     */
    public suspend fun onAfterLLMCall(responses: List<Message.Response>) {
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallHandler.handle(responses) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Notifies all registered tool handlers when a tool is called.
     *
     * @param tool The tool that is being called
     * @param toolArgs The arguments provided to the tool
     */
    public suspend fun onToolCall(tool: Tool<*, *>, toolArgs: Tool.Args) {
        executeToolHandlers.values.forEach { handler -> handler.toolCallHandler.handle(tool, toolArgs) }
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param tool The tool for which validation failed
     * @param toolArgs The arguments that failed validation
     * @param error The validation error message
     */
    public suspend fun onToolValidationError(tool: Tool<*, *>, toolArgs: Tool.Args, error: String) {
        executeToolHandlers.values.forEach { handler ->
            handler.toolValidationErrorHandler.handle(
                tool,
                toolArgs,
                error
            )
        }
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param tool The tool that failed
     * @param toolArgs The arguments provided to the tool
     * @param throwable The exception that caused the failure
     */
    public suspend fun onToolCallFailure(tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) {
        executeToolHandlers.values.forEach { handler ->
            handler.toolCallFailureHandler.handle(
                tool,
                toolArgs,
                throwable
            )
        }
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param tool The tool that was called
     * @param toolArgs The arguments that were provided to the tool
     * @param result The result produced by the tool, or null if no result was produced
     */
    public suspend fun onToolCallResult(tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) {
        executeToolHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(tool, toolArgs, result) }
    }

    //endregion Trigger Tool Call Handlers

    //region Interceptors

    /**
     * Set feature handler for Context Stage events
     *
     * @param handler The handler responsible for processing the feature within the stage context
     *
     * Example:
     * ```
     * pipeline.interceptContextStageFeature(MyFeature) { stageContext: AIAgentStageContext ->
     *   // Inspect stage context
     * }
     * ```
     */
    public fun <TFeature : Any> interceptContextAgentFeature(
        feature: AIAgentFeature<*, TFeature>,
        handler: AgentContextHandler<TFeature>,
    ) {
        agentContextHandler[feature.key] = handler
    }

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This method registers a transformer function that will be called when an agent environment
     * is being created, allowing the feature to customize the environment based on the agent context.
     *
     * @param feature The feature for which to register the environment transformer
     * @param featureImpl The implementation of the feature
     * @param transform A function that transforms the environment, with access to the agent creation context
     *
     * Example:
     * ```
     * pipeline.interceptEnvironmentCreated(MyFeature, myFeatureImpl) { environment ->
     *     // Modify the environment based on agent context
     *     environment.copy(
     *         variables = environment.variables + mapOf("customVar" to "value")
     *     )
     * }
     * ```
     */
    public fun <TFeature : Any> interceptEnvironmentCreated(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        transform: AgentCreateContext<TFeature>.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) } as? AgentHandler<TFeature> ?: return

        existingHandler.environmentTransformer = AgentEnvironmentTransformer { context, env -> context.transform(env) }
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeAgentStarted(MyFeature, myFeatureImpl) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeAgentStarted(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend AgentStartContext<TFeature>.() -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) } as? AgentHandler<TFeature> ?: return

        existingHandler.beforeAgentStartedHandler = BeforeAgentStartedHandler { handle(it) }
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentFinished(MyFeature, myFeatureImpl) { strategyName, result ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentFinished(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(strategyName: String, result: String?) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) }

        existingHandler.agentFinishedHandler = AgentFinishedHandler { strategyName, result ->
            with(featureImpl) { handle(strategyName, result) }
        }
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentRunError(MyFeature, myFeatureImpl) { strategyName, throwable ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentRunError(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(strategyName: String, throwable: Throwable) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) }

        existingHandler.agentRunErrorHandler = AgentRunErrorHandler { strategyName, throwable ->
            with(featureImpl) { handle(strategyName, throwable) }
        }
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarted(MyFeature, myFeatureImpl) {
     *     val strategyId = strategy.id
     *     logger.info("Strategy $strategyId has started execution")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyStarted(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend StrategyUpdateContext<TFeature>.() -> Unit
    ) {
        val existingHandler = strategyHandlers.getOrPut(feature.key) { StrategyHandler(featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${featureImpl::class}>, but get a handler of type <${feature.key}> instead. " +
                        "Skipping adding strategy started interceptor for feature."
            }
            return
        }

        existingHandler.strategyStartedHandler = StrategyStartedHandler { updateContext ->
            handle(updateContext)
        }
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * @param handle A suspend function that processes the completion of a strategy, accepting the strategy name
     *               and its result as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptStrategyFinished(MyFeature, myFeatureImpl) { strategyName, result ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyFinished(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(strategyName: String, result: String) -> Unit
    ) {
        val existingHandler = strategyHandlers.getOrPut(feature.key) { StrategyHandler(featureImpl) }

        existingHandler.strategyFinishedHandler = StrategyFinishedHandler { strategyName, result ->
            with(featureImpl) { handle(strategyName, result) }
        }
    }

    /**
     * Intercepts node execution before it starts.
     *
     * @param handle The handler that processes before-node events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeNode(MyFeature, myFeatureImpl) { node, context, input ->
     *     logger.info("Node ${node.name} is about to execute with input: $input")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeNode(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?) -> Unit
    ) {
        val existingHandler = executeNodeHandlers.getOrPut(feature.key) { ExecuteNodeHandler() }

        existingHandler.beforeNodeHandler = BeforeNodeHandler { node, context, input ->
            with(featureImpl) { handle(node, context, input) }
        }
    }

    /**
     * Intercepts node execution after it completes.
     *
     * @param handle The handler that processes after-node events
     *
     * Example:
     * ```
     * pipeline.interceptAfterNode(MyFeature, myFeatureImpl) { node, context, input, output ->
     *     logger.info("Node ${node.name} executed with input: $input and produced output: $output")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAfterNode(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(
            node: AIAgentNodeBase<*, *>,
            context: AIAgentContextBase,
            input: Any?,
            output: Any?
        ) -> Unit
    ) {
        val existingHandler = executeNodeHandlers.getOrPut(feature.key) { ExecuteNodeHandler() }

        existingHandler.afterNodeHandler = AfterNodeHandler { node, context, input, output ->
            with(featureImpl) { handle(node, context, input, output) }
        }
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeLLMCall(MyFeature, myFeatureImpl) { prompt ->
     *     logger.info("About to make LLM call with prompt: ${prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeLLMCall(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(prompt: Prompt, tools: List<ToolDescriptor>) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(feature.key) { ExecuteLLMHandler() }

        existingHandler.beforeLLMCallHandler = BeforeLLMCallHandler { prompt, tools ->
            with(featureImpl) { handle(prompt, tools) }
        }
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptAfterLLMCall(MyFeature, myFeatureImpl) { response ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAfterLLMCall(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(responses: List<Message.Response>) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(feature.key) { ExecuteLLMHandler() }

        existingHandler.afterLLMCallHandler = AfterLLMCallHandler { responses ->
            with(featureImpl) { handle(responses) }
        }
    }

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     *
     * @param handle A suspend lambda function that processes tool calls, taking the tool, and its arguments as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptToolCall(MyFeature, myFeatureImpl) { tool, toolArgs ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCall(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallHandler = ToolCallHandler { tool, toolArgs ->
            with(featureImpl) { handle(tool, toolArgs) }
        }
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * @param handle A suspendable lambda function that will be invoked when a tool validation error occurs.
     *        The lambda provides the tool's stage, tool instance, tool arguments, and the value that caused the validation error.
     *
     * Example:
     * ```
     * pipeline.interceptToolValidationError(MyFeature, myFeatureImpl) { tool, toolArgs, value ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolValidationError(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolValidationErrorHandler = ToolValidationErrorHandler { tool, toolArgs, value ->
            with(featureImpl) { handle(tool, toolArgs, value) }
        }
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * @param handle A suspend function that is invoked when a tool call fails. It provides the stage,
     *               the tool, the tool arguments, and the throwable that caused the failure.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallFailure(MyFeature, myFeatureImpl) { tool, toolArgs, throwable ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCallFailure(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallFailureHandler = ToolCallFailureHandler { tool, toolArgs, throwable ->
            with(featureImpl) { handle(tool, toolArgs, throwable) }
        }
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * @param handle A suspending function that defines the behavior to execute when a tool call result is intercepted.
     * The function takes as parameters the stage of the tool call, the tool being called, its arguments,
     * and the result of the tool call if available.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallResult(MyFeature, myFeatureImpl) { tool, toolArgs, result ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCallResult(
        feature: AIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallResultHandler = ToolCallResultHandler { tool, toolArgs, result ->
            with(featureImpl) { handle(tool, toolArgs, result) }
        }
    }

    //endregion Interceptors
}
