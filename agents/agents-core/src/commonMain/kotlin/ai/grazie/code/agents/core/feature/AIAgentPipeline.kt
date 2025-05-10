package ai.grazie.code.agents.core.feature

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorageKey
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.annotation.InternalAgentsApi
import ai.grazie.code.agents.core.environment.AgentEnvironment
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.feature.config.FeatureConfig
import ai.grazie.code.agents.core.feature.handler.*
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.ToolStage
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
class AIAgentPipeline {

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

    /**
     * Run registered features' handlers on agent strategy started event.
     */
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

    /**
     * Run registered features' handlers on before node execution.
     *
     * @param node The node that has been executed;
     * @param context The stage context in which the node was executed;
     * @param input The input data provided to the node during its execution.
     */
    suspend fun onBeforeNode(node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?) {
        executeNodeHandlers.values.forEach { handler -> handler.beforeNodeHandler.handle(node, context, input) }
    }

    /**
     * Run registered features' handlers on after node execution.
     *
     * @param node The node that has been executed;
     * @param context The stage context in which the node was executed;
     * @param input The input data provided to the node during its execution;
     * @param output The output data produced by the node after execution.
     */
    suspend fun onAfterNode(node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any?) {
        executeNodeHandlers.values.forEach { handler -> handler.afterNodeHandler.handle(node, context, input, output) }
    }

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    /**
     * Run registered features' handler on before making a call to the LLM.
     *
     * @param prompt The prompt containing input parameters and messages to be sent to the model.
     */
    suspend fun onBeforeLLMCall(prompt: Prompt) {
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallHandler.handle(prompt) }
    }

    /**
     * Run registered features' handler on before making a call with a list of tools to the LLM.
     *
     * @param prompt The prompt containing input parameters and messages to be sent to the model;
     * @param tools A list of tool descriptors that are included in the context of the LLM call.
     */
    suspend fun onBeforeLLMWithToolsCall(prompt: Prompt, tools: List<ToolDescriptor>) {
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallWithToolsHandler.handle(prompt, tools) }
    }

    /**
     * Run registered features' handler on after making a call to the LLM.
     *
     * @param response The response received from the LLM call.
     */
    suspend fun onAfterLLMCall(response: String) {
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallHandler.handle(response) }
    }

    /**
     * Run registered features' handler on after making a call with a list of tools to the LLM.
     *
     * @param response The response object received from the LLM call, containing the content and role.
     */
    suspend fun onAfterLLMWithToolsCall(response: List<Message.Response>, tools: List<ToolDescriptor>) {
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallWithToolsHandler.handle(response, tools) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Run registered features' handler on before tool call.
     *
     * @param tools The signature of the tool being called, including its name and parameters.
     */
    suspend fun onBeforeToolCalls(tools: List<Message.Tool.Call>) {
        executeToolHandlers.values.forEach { handler -> handler.beforeToolCallsHandler.handle(tools) }
    }

    /**
     * Run registered features' handler on after tool call.
     *
     * @param results The result object of the tool call, containing the tool name and its output content.
     */
    suspend fun onAfterToolCalls(results: List<ReceivedToolResult>) {
        executeToolHandlers.values.forEach { handler -> handler.afterToolCallsHandler.handle(results) }
    }

    suspend fun onToolCall(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args) {
        executeToolHandlers.values.forEach { handler -> handler.toolCallHandler.handle(stage, tool, toolArgs) }
    }

    suspend fun onToolValidationError(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, value: String) {
        executeToolHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(stage, tool, toolArgs, value) }
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
     * @param TFeature The type of the feature being handled
     * @param feature The feature to be intercepted in the stage context
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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

    fun <TFeature: Any> interceptAgentRunError(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(agentName: String, throwable: Throwable) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(feature.key) { AgentHandler(featureImpl) }

        existingHandler.agentRunErrorHandler = AgentRunErrorHandler { strategyName, throwable ->
            with(featureImpl) { handle(strategyName, throwable) }
        }
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
     * @param handle The handler that processes strategy started events
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
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
     * Intercepts tool calls before they are made to modify or log the tool signature.
     *
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
     * @param handle The handler that processes before-tool-call events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeToolCall(MyFeature, myFeatureImpl) { tool ->
     *     // Validate or modify tool arguments before execution
     * }
     * ```
     */
    fun <TFeature : Any> interceptBeforeToolCall(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(tools: List<Message.Tool.Call>) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.beforeToolCallsHandler = BeforeToolCallsHandler { tools ->
            with(featureImpl) { handle(tools) }
        }
    }

    /**
     * Intercepts tool calls after they are made to process or log the tool result.
     *
     * @param TFeature The type of feature being installed
     * @param feature The feature definition
     * @param featureImpl The feature implementation instance
     * @param handle The handler that processes after-tool-call events
     *
     * Example:
     * ```
     * pipeline.interceptAfterToolCall(MyFeature, myFeatureImpl) { result ->
     *     // Process or analyze the tool execution result
     * }
     * ```
     */
    fun <TFeature : Any> interceptAfterToolCall(
        feature: KotlinAIAgentFeature<*, TFeature>,
        featureImpl: TFeature,
        handle: suspend TFeature.(results: List<ReceivedToolResult>) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(feature.key) { ExecuteToolHandler() }

        existingHandler.afterToolCallsHandler = AfterToolCallsHandler { results ->
            with(featureImpl) { handle(results) }
        }
    }

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
