package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorageKey
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.feature.handler.*
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.grazie.code.agents.local.features.common.config.FeatureConfig
import ai.grazie.utils.mpp.LoggerFactory
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.awaitAll

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
class AgentPipeline {

    companion object {
        private val logger = LoggerFactory.create("ai.grazie.code.agents.core.pipeline.AIAgentPipeline")
    }

    private val registeredFeatures: MutableMap<LocalAgentStorageKey<*>, FeatureConfig> = mutableMapOf()

    private val agentHandlers: MutableMap<LocalAgentStorageKey<*>, AgentHandler<*>> = mutableMapOf()

    private val strategyHandlers: MutableMap<LocalAgentStorageKey<*>, StrategyHandler<*>> = mutableMapOf()

    private val stageContextHandler: MutableMap<LocalAgentStorageKey<*>, StageContextHandler<*>> = mutableMapOf()

    private val executeNodeHandlers: MutableMap<LocalAgentStorageKey<*>, ExecuteNodeHandler> = mutableMapOf()

    private val executeToolHandlers: MutableMap<LocalAgentStorageKey<*>, ExecuteToolHandler> = mutableMapOf()

    private val executeLLMHandlers: MutableMap<LocalAgentStorageKey<*>, ExecuteLLMHandler> = mutableMapOf()

    /**
     * Installs a feature into the pipeline with the provided configuration.
     *
     * @param Config The type of the feature configuration;
     * @param Feature The type of the feature being installed;
     * @param feature The feature implementation to be installed;
     * @param configure A lambda to customize the feature configuration.
     */
    suspend fun <Config : FeatureConfig, Feature : Any> install(
        feature: KotlinAIAgentFeature<Config, Feature>,
        configure: Config.() -> Unit
    ) {
        val config = feature.createInitialConfig().apply { configure() }
        config.messageProcessor.forEach { provider -> provider.initialize() }
        feature.install(
            config = config,
            pipeline = this,
        )

        registeredFeatures[feature.key] = config
    }

    internal suspend fun awaitFeaturesStreamProvidersReady() {
        registeredFeatures.values.flatMap { config -> config.messageProcessor.map { provider -> provider.isReady } }
            .awaitAll()
    }

    internal suspend fun closeFeaturesStreamProviders() {
        registeredFeatures.values.forEach { config -> config.messageProcessor.forEach { provider -> provider.close() } }
    }

    //region Trigger Agent Handlers

    /**
     * Run registered features' handlers on the event - agent created.
     */
    @OptIn(InternalAgentsApi::class)
    suspend fun onAgentCreated(strategy: LocalAgentStrategy, agent: AIAgentBase) {
        agentHandlers.values.forEach { handler ->
            val updateContext = AgentCreateContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.handleAgentCreatedUnsafe(updateContext)
        }
    }

    suspend fun onAgentStarted(strategyName: String) {
        agentHandlers.values.forEach { handler -> handler.agentStartedHandler.handle(strategyName) }
    }

    suspend fun onAgentFinished(strategyName: String, result: String?) {
        agentHandlers.values.forEach { handler -> handler.agentFinishedHandler.handle(strategyName, result) }
    }

    suspend fun onAgentRunError(strategyName: String, throwable: Throwable) {
        agentHandlers.values.forEach { handler -> handler.agentRunErrorHandler.handle(strategyName, throwable) }
    }

    fun transformEnvironment(
        strategy: LocalAgentStrategy,
        agent: AIAgentBase,
        baseEnvironment: AgentEnvironment
    ): AgentEnvironment {
        return agentHandlers.values.fold(baseEnvironment) { env, handler ->
            val context = AgentCreateContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.transformEnvironmentUnsafe(context, env)
        }
    }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    suspend fun onStrategyStarted(strategy: LocalAgentStrategy) {
        strategyHandlers.values.forEach { handler ->
            val updateContext = StrategyUpdateContext(strategy, handler.feature)
            handler.handleStrategyStartedUnsafe(updateContext)
        }
    }

    suspend fun onStrategyFinished(strategyName: String, result: String) {
        strategyHandlers.values.forEach { handler -> handler.strategyFinishedHandler.handle(strategyName, result) }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger Stage Context Handlers

    /**
     * Run registered features' handlers on stage execute event.
     * Retrieves the features associated with the current stage context.
     */
    fun getStageFeatures(context: LocalAgentStageContext): Map<LocalAgentStorageKey<*>, Any> {
        return stageContextHandler.mapValues { (_, featureProvider) ->
            featureProvider.handle(context)
        }
    }

    //endregion Trigger Stage Context Handlers

    //region Trigger Node Handlers

    suspend fun onBeforeNode(node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?) {
        executeNodeHandlers.values.forEach { handler -> handler.beforeNodeHandler.handle(node, context, input) }
    }

    suspend fun onAfterNode(node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any?) {
        executeNodeHandlers.values.forEach { handler -> handler.afterNodeHandler.handle(node, context, input, output) }
    }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    suspend fun onBeforeLLMCall(prompt: Prompt) {
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallHandler.handle(prompt) }
    }

    suspend fun onBeforeLLMWithToolsCall(prompt: Prompt, tools: List<ToolDescriptor>) {
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallWithToolsHandler.handle(prompt, tools) }
    }

    suspend fun onAfterLLMCall(response: String) {
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallHandler.handle(response) }
    }

    suspend fun onAfterLLMWithToolsCall(response: List<Message.Response>, tools: List<ToolDescriptor>) {
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallWithToolsHandler.handle(response, tools) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    suspend fun onToolCall(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args) {
        executeToolHandlers.values.forEach { handler -> handler.toolCallHandler.handle(stage, tool, toolArgs) }
    }

    suspend fun onToolValidationError(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, error: String) {
        executeToolHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(stage, tool, toolArgs, error) }
    }

    suspend fun onToolCallFailure(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) {
        executeToolHandlers.values.forEach { handler -> handler.toolCallFailureHandler.handle(stage, tool, toolArgs,  throwable) }
    }

    suspend fun onToolCallResult(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) {
        executeToolHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(stage, tool, toolArgs, result) }
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
     * pipeline.interceptContextStageFeature(MyFeature) { stageContext: LocalAgentStageContext ->
     *   // Inspect stage context
     * }
     * ```
     */
    fun <TFeature : Any> interceptContextStageFeature(
        feature: KotlinAIAgentFeature<*, TFeature>,
        handler: StageContextHandler<TFeature>,
    ) {
        stageContextHandler[feature.key] = handler
    }

    /**
     * Intercepts agent creation to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptAgentCreation(MyFeature, myFeatureImpl) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    fun <TFeature : Any> interceptAgentCreated(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend AgentCreateContext<TFeature>.() -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) } as? AgentHandler<TFeature> ?: return

        existingHandler.agentCreatedHandler = AgentCreatedHandler { handle(it) }
    }

    fun <TFeature : Any> interceptEnvironmentCreated(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        transform: AgentCreateContext<TFeature>.(AgentEnvironment) -> AgentEnvironment
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) } as? AgentHandler<TFeature> ?: return

        existingHandler.environmentTransformer = AgentEnvironmentTransformer { context, env -> context.transform(env) }
    }

    /**
     * Intercepts the agent's start event and binds a custom handler to execute specific behavior
     * when the agent starts operating with a given strategy.
     *
     * @param handle A suspend function providing custom logic to execute when the agent starts,
     *               with the active strategy name as a parameter.
     *
     * Example:
     * ```
     * pipeline.interceptAgentStarted(MyFeature, myFeatureImpl) { strategyName ->
     *     // Handle the agent starting here, using the active strategy name.
     * }
     * ```
     */
    fun <TFeature: Any> interceptAgentStarted(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(strategyName: String) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) }

        existingHandler.agentStartedHandler = AgentStartedHandler { strategyName ->
            with(featureImpl) { handle(strategyName) }
        }
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
    fun <TFeature: Any> interceptAgentFinished(
        feature: KotlinAIAgentFeature<*, TFeature>,
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
    fun <TFeature: Any> interceptAgentRunError(
        feature: KotlinAIAgentFeature<*, TFeature>,
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
    fun <TFeature : Any> interceptStrategyStarted(
        feature: KotlinAIAgentFeature<*, TFeature>,
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
    fun <TFeature : Any> interceptStrategyFinished(
        feature: KotlinAIAgentFeature<*, TFeature>,
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
    fun <TFeature : Any> interceptBeforeNode(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?) -> Unit
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
    fun <TFeature : Any> interceptAfterNode(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(
            node: LocalAgentNode<*, *>,
            context: LocalAgentStageContext,
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
    fun <TFeature : Any> interceptBeforeLLMCall(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(prompt: Prompt) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(feature.key) { ExecuteLLMHandler() }

        existingHandler.beforeLLMCallHandler = BeforeLLMCallHandler { prompt ->
            with(featureImpl) { handle(prompt) }
        }
    }

    /**
     * Intercepts LLM calls with tools before they are made to modify or log the prompt and tools.
     *
     * @param handle The handler that processes before-LLM-call-with-tools events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeLLMCallWithTools(MyFeature, myFeatureImpl) { prompt, tools ->
     *     // Inspect or modify the tools list before the call
     * }
     * ```
     */
    fun <TFeature : Any> interceptBeforeLLMCallWithTools(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(prompt: Prompt, tools: List<ToolDescriptor>) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(feature.key) { ExecuteLLMHandler() }

        existingHandler.beforeLLMCallWithToolsHandler = BeforeLLMCallWithToolsHandler { prompt, tools ->
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
    fun <TFeature : Any> interceptAfterLLMCall(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(response: String) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(feature.key) { ExecuteLLMHandler() }

        existingHandler.afterLLMCallHandler = AfterLLMCallHandler { response ->
            with(featureImpl) { handle(response) }
        }
    }

    /**
     * Intercepts LLM calls with tools after they are made to process or log the structured response.
     *
     * @param handle The handler that processes after-LLM-call-with-tools events
     *
     * Example:
     * ```
     * pipeline.interceptAfterLLMCallWithTools(MyFeature, myFeatureImpl) { response ->
     *     // Process the structured response
     * }
     * ```
     */
    fun <TFeature : Any> interceptAfterLLMCallWithTools(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(responses: List<Message.Response>, tools: List<ToolDescriptor>) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(feature.key) { ExecuteLLMHandler() }

        existingHandler.afterLLMCallWithToolsHandler = AfterLLMCallWithToolsHandler { responses, tools ->
            with(featureImpl) { handle(responses, tools) }
        }
    }

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     *
     * @param handle A suspend lambda function that processes tool calls, taking the current stage, the tool, and its arguments as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptToolCall(MyFeature, myFeatureImpl) { stage, tool, toolArgs ->
     *    // Process or log the tool call
     * }
     * ```
     */
    fun <TFeature: Any> interceptToolCall(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallHandler = ToolCallHandler { stage, tool, toolArgs ->
            with(featureImpl) { handle(stage, tool, toolArgs) }
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
     * pipeline.interceptToolValidationError(MyFeature, myFeatureImpl) { stage, tool, toolArgs, value ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    fun <TFeature: Any> interceptToolValidationError(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolValidationErrorHandler = ToolValidationErrorHandler { stage, tool, toolArgs, value ->
            with(featureImpl) { handle(stage, tool, toolArgs, value) }
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
     * pipeline.interceptToolCallFailure(MyFeature, myFeatureImpl) { stage, tool, toolArgs, throwable ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    fun <TFeature: Any> interceptToolCallFailure(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallFailureHandler = ToolCallFailureHandler { stage, tool, toolArgs, throwable ->
            with(featureImpl) { handle(stage, tool, toolArgs, throwable) }
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
     * pipeline.interceptToolCallResult(MyFeature, myFeatureImpl) { stage, tool, toolArgs, result ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    fun <TFeature: Any> interceptToolCallResult(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallResultHandler = ToolCallResultHandler { stage, tool, toolArgs, result ->
            with(featureImpl) { handle(stage, tool, toolArgs, result) }
        }
    }

    //endregion Interceptors
}
