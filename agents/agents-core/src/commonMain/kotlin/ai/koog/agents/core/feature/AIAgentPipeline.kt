package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AfterLLMCallContext
import ai.koog.agents.core.feature.handler.AfterLLMCallHandler
import ai.koog.agents.core.feature.handler.AfterStreamContext
import ai.koog.agents.core.feature.handler.AfterStreamHandler
import ai.koog.agents.core.feature.handler.AgentBeforeCloseContext
import ai.koog.agents.core.feature.handler.AgentBeforeCloseHandler
import ai.koog.agents.core.feature.handler.AgentContextHandler
import ai.koog.agents.core.feature.handler.AgentEnvironmentTransformer
import ai.koog.agents.core.feature.handler.AgentFinishedContext
import ai.koog.agents.core.feature.handler.AgentFinishedHandler
import ai.koog.agents.core.feature.handler.AgentHandler
import ai.koog.agents.core.feature.handler.AgentRunErrorContext
import ai.koog.agents.core.feature.handler.AgentRunErrorHandler
import ai.koog.agents.core.feature.handler.AgentStartContext
import ai.koog.agents.core.feature.handler.AgentTransformEnvironmentContext
import ai.koog.agents.core.feature.handler.BeforeAgentStartedHandler
import ai.koog.agents.core.feature.handler.BeforeLLMCallContext
import ai.koog.agents.core.feature.handler.BeforeLLMCallHandler
import ai.koog.agents.core.feature.handler.BeforeStreamContext
import ai.koog.agents.core.feature.handler.BeforeStreamHandler
import ai.koog.agents.core.feature.handler.ExecuteLLMHandler
import ai.koog.agents.core.feature.handler.ExecuteToolHandler
import ai.koog.agents.core.feature.handler.StrategyFinishContext
import ai.koog.agents.core.feature.handler.StrategyFinishedHandler
import ai.koog.agents.core.feature.handler.StrategyHandler
import ai.koog.agents.core.feature.handler.StrategyStartContext
import ai.koog.agents.core.feature.handler.StrategyStartedHandler
import ai.koog.agents.core.feature.handler.StreamErrorContext
import ai.koog.agents.core.feature.handler.StreamErrorHandler
import ai.koog.agents.core.feature.handler.StreamFrameContext
import ai.koog.agents.core.feature.handler.StreamFrameHandler
import ai.koog.agents.core.feature.handler.StreamHandler
import ai.koog.agents.core.feature.handler.ToolCallContext
import ai.koog.agents.core.feature.handler.ToolCallFailureContext
import ai.koog.agents.core.feature.handler.ToolCallFailureHandler
import ai.koog.agents.core.feature.handler.ToolCallHandler
import ai.koog.agents.core.feature.handler.ToolCallResultContext
import ai.koog.agents.core.feature.handler.ToolCallResultHandler
import ai.koog.agents.core.feature.handler.ToolValidationErrorContext
import ai.koog.agents.core.feature.handler.ToolValidationErrorHandler
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.reflect.KType

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into agent contexts
 * - Intercept agent creation and environment transformation
 * - Intercept strategy execution before and after it happens
 * - Intercept node execution before and after it happens
 * - Intercept LLM (Language Learning Model) calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 *
 * @param clock Clock instance for time-related operations
 */
public abstract class AIAgentPipeline(public val clock: Clock) {

    /**
     * Companion object for the AIAgentPipeline class.
     */
    private companion object {
        /**
         * Logger instance for the AIAgentPipeline class.
         */
        private val logger = KotlinLogging.logger { }
    }

    private val featurePrepareDispatcher = Dispatchers.Default.limitedParallelism(5)

    /**
     * Map of registered features and their configurations.
     * Keys are feature storage keys, values are feature configurations.
     */
    protected val registeredFeatures: MutableMap<AIAgentStorageKey<*>, FeatureConfig> = mutableMapOf()

    /**
     * Map of agent handlers registered for different features.
     * Keys are feature storage keys, values are agent handlers.
     */
    protected val agentHandlers: MutableMap<AIAgentStorageKey<*>, AgentHandler<*>> = mutableMapOf()

    /**
     * Map of strategy handlers registered for different features.
     * Keys are feature storage keys, values are strategy handlers.
     */
    protected val strategyHandlers: MutableMap<AIAgentStorageKey<*>, StrategyHandler<*>> = mutableMapOf()

    /**
     * Map of agent context handlers registered for different features.
     * Keys are feature storage keys, values are agent context handlers.
     */
    protected val agentContextHandler: MutableMap<AIAgentStorageKey<*>, AgentContextHandler<*>> = mutableMapOf()

    /**
     * Map of tool execution handlers registered for different features.
     * Keys are feature storage keys, values are tool execution handlers.
     */
    protected val executeToolHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteToolHandler> = mutableMapOf()

    /**
     * Map of LLM execution handlers registered for different features.
     * Keys are feature storage keys, values are LLM execution handlers.
     */
    protected val executeLLMHandlers: MutableMap<AIAgentStorageKey<*>, ExecuteLLMHandler> = mutableMapOf()

    /**
     * Map of feature storage keys to their stream handlers.
     * These handlers manage the streaming lifecycle events (before, during, and after streaming).
     */
    protected val streamHandlers: MutableMap<AIAgentStorageKey<*>, StreamHandler> = mutableMapOf()

    internal suspend fun prepareFeatures() {
        withContext(featurePrepareDispatcher) {
            registeredFeatures.values.forEach { featureConfig ->
                featureConfig.messageProcessors.map { processor ->
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
        registeredFeatures.values.forEach { config -> config.messageProcessors.forEach { provider -> provider.close() } }
    }

    //region Trigger Agent Handlers

    /**
     * Notifies all registered handlers that an agent has started execution.
     *
     * @param runId The unique identifier for the agent run
     * @param agent The agent instance for which the execution has started
     * @param context The context of the agent execution, providing access to the agent environment and context features
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun <TInput, TOutput> onBeforeAgentStarted(
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        agentHandlers.values.forEach { handler ->
            val eventContext =
                AgentStartContext(
                    agent = agent,
                    runId = runId,
                    feature = handler.feature,
                    context = context
                )
            handler.handleBeforeAgentStartedUnsafe(eventContext)
        }
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     */
    public suspend fun onAgentFinished(
        agentId: String,
        runId: String,
        result: Any?,
        resultType: KType,
    ) {
        val eventContext =
            AgentFinishedContext(agentId = agentId, runId = runId, result = result, resultType = resultType)
        agentHandlers.values.forEach { handler -> handler.agentFinishedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The exception that was thrown during agent execution
     */
    public suspend fun onAgentRunError(
        agentId: String,
        runId: String,
        throwable: Throwable
    ) {
        val eventContext = AgentRunErrorContext(agentId = agentId, runId = runId, throwable = throwable)
        agentHandlers.values.forEach { handler -> handler.agentRunErrorHandler.handle(eventContext) }
    }

    /**
     * Invoked before streaming from a language model begins.
     *
     * This method notifies all registered stream handlers that streaming is about to start,
     * allowing them to perform preprocessing or logging operations.
     *
     * @param runId The unique identifier for this streaming session
     * @param prompt The prompt being sent to the language model
     * @param model The language model being used for streaming
     * @param tools The list of available tool descriptors for this streaming session
     */
    public suspend fun onBeforeStream(runId: String, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>) {
        val eventContext = BeforeStreamContext(runId, prompt, model, tools)
        streamHandlers.values.forEach { handler -> handler.beforeStreamHandler.handle(eventContext) }
    }

    /**
     * Invoked when a stream frame is received during the streaming process.
     *
     * This method notifies all registered stream handlers about each incoming stream frame,
     * allowing them to process, transform, or aggregate the streaming content in real-time.
     *
     * @param runId The unique identifier for this streaming session
     * @param streamFrame The individual stream frame containing partial response data
     */
    public suspend fun onStreamFrame(runId: String, streamFrame: StreamFrame) {
        val eventContext = StreamFrameContext(runId, streamFrame)
        streamHandlers.values.forEach { handler -> handler.streamFrameHandler.handle(eventContext) }
    }

    /**
     * Invoked if an error occurs during the streaming process.
     *
     * This method notifies all registered stream handlers about the streaming error,
     * allowing them to handle or log the error.
     *
     * @param runId The unique identifier for this streaming session
     * @param throwable The exception that occurred during streaming, if applicable
     */
    public suspend fun onStreamError(runId: String, throwable: Throwable) {
        val eventContext = StreamErrorContext(runId, throwable)
        streamHandlers.values.forEach { handler -> handler.streamErrorHandler.handle(eventContext) }
    }

    /**
     * Invoked after streaming from a language model completes.
     *
     * This method notifies all registered stream handlers that streaming has finished,
     * allowing them to perform post-processing, cleanup, or final logging operations.
     *
     * @param runId The unique identifier for this streaming session
     * @param prompt The prompt that was sent to the language model
     * @param model The language model that was used for streaming
     * @param tools The list of tool descriptors that were available for this streaming session
     */
    public suspend fun onAfterStream(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        val eventContext = AfterStreamContext(runId, prompt, model, tools)
        streamHandlers.values.forEach { handler -> handler.afterStreamHandler.handle(eventContext) }
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public suspend fun onAgentBeforeClosed(
        agentId: String
    ) {
        val eventContext = AgentBeforeCloseContext(agentId = agentId)
        agentHandlers.values.forEach { handler -> handler.agentBeforeCloseHandler.handle(eventContext) }
    }

    /**
     * Retrieves all features associated with the given agent context.
     *
     * This method collects features from all registered agent context handlers
     * that are applicable to the provided context.
     *
     * @param context The agent context for which to retrieve features
     * @return A map of feature keys to their corresponding feature instances
     */
    public fun getAgentFeatures(context: AIAgentContext): Map<AIAgentStorageKey<*>, Any> {
        return agentContextHandler.mapValues { (_, featureProvider) ->
            featureProvider.handle(context)
        }
    }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyStarted(strategy: AIAgentStrategy<*, *, *>, context: AIAgentContext) {
        strategyHandlers.values.forEach { handler ->
            val eventContext = StrategyStartContext(
                runId = context.runId,
                strategy = strategy,
                feature = handler.feature,
                context = context
            )
            handler.handleStrategyStartedUnsafe(eventContext)
        }
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param strategy The strategy that has finished execution
     * @param context The context of the strategy execution
     * @param result The result produced by the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyFinished(
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType,
    ) {
        strategyHandlers.values.forEach { handler ->
            val eventContext = StrategyFinishContext(
                runId = context.runId,
                strategy = strategy,
                feature = handler.feature,
                result = result,
                resultType = resultType
            )
            handler.handleStrategyFinishedUnsafe(eventContext)
        }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger Node Handlers

    //endregion Trigger Node Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param prompt The prompt that will be sent to the language model
     * @param tools The list of tool descriptors available for the LLM call
     * @param model The language model instance that will process the request
     */
    public suspend fun onBeforeLLMCall(runId: String, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>) {
        val eventContext = BeforeLLMCallContext(runId, prompt, model, tools)
        executeLLMHandlers.values.forEach { handler -> handler.beforeLLMCallHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param runId Identifier for the current run.
     * @param prompt The prompt that was sent to the language model
     * @param tools The list of tool descriptors that were available for the LLM call
     * @param model The language model instance that processed the request
     * @param responses The response messages received from the language model
     */
    public suspend fun onAfterLLMCall(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult? = null,
    ) {
        val eventContext = AfterLLMCallContext(runId, prompt, model, tools, responses, moderationResponse)
        executeLLMHandlers.values.forEach { handler -> handler.afterLLMCallHandler.handle(eventContext) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Notifies all registered tool handlers when a tool is called.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that is being called
     * @param toolArgs The arguments provided to the tool
     */
    public suspend fun onToolCall(runId: String, toolCallId: String?, tool: Tool<*, *>, toolArgs: ToolArgs) {
        val eventContext = ToolCallContext(runId, toolCallId, tool, toolArgs)
        executeToolHandlers.values.forEach { handler -> handler.toolCallHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool for which validation failed
     * @param toolArgs The arguments that failed validation
     * @param error The validation error message
     */
    public suspend fun onToolValidationError(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        error: String
    ) {
        val eventContext =
            ToolValidationErrorContext(runId, toolCallId, tool, toolArgs, error)
        executeToolHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that failed
     * @param toolArgs The arguments provided to the tool
     * @param throwable The exception that caused the failure
     */
    public suspend fun onToolCallFailure(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        throwable: Throwable
    ) {
        val eventContext = ToolCallFailureContext(runId, toolCallId, tool, toolArgs, throwable)
        executeToolHandlers.values.forEach { handler -> handler.toolCallFailureHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that was called
     * @param toolArgs The arguments that were provided to the tool
     * @param result The result produced by the tool, or null if no result was produced
     */
    public suspend fun onToolCallResult(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        result: ToolResult?
    ) {
        val eventContext = ToolCallResultContext(runId, toolCallId, tool, toolArgs, result)
        executeToolHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(eventContext) }
    }

    //endregion Trigger Tool Call Handlers

    //region Interceptors

    /**
     * Sets a feature handler for agent context events.
     *
     * @param feature The feature for which to register the handler
     * @param handler The handler responsible for processing the feature within the agent context
     *
     * Example:
     * ```
     * pipeline.interceptContextAgentFeature(MyFeature) { agentContext ->
     *   // Inspect agent context
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
     * @param context The context of the feature being intercepted, providing access to the feature key and implementation
     * @param transform A function that transforms the environment, with access to the agent creation context
     *
     * Example:
     * ```
     * pipeline.interceptEnvironmentCreated(InterceptContext) { environment ->
     *     // Modify the environment based on agent context
     *     environment.copy(
     *         variables = environment.variables + mapOf("customVar" to "value")
     *     )
     * }
     * ```
     */
    public fun <TFeature : Any> interceptEnvironmentCreated(
        context: InterceptContext<TFeature>,
        transform: AgentTransformEnvironmentContext<TFeature>.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) } as? AgentHandler<TFeature>
                ?: return

        existingHandler.environmentTransformer = AgentEnvironmentTransformer { context, env -> context.transform(env) }
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeAgentStarted(InterceptContext) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeAgentStarted(
        context: InterceptContext<TFeature>,
        handle: suspend (AgentStartContext<TFeature>) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val existingHandler: AgentHandler<TFeature> =
            agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) } as? AgentHandler<TFeature>
                ?: return

        existingHandler.beforeAgentStartedHandler = BeforeAgentStartedHandler { context ->
            handle(context)
        }
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentFinished(InterceptContext { eventContext ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentFinished(
        context: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentFinishedContext) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) }

        existingHandler.agentFinishedHandler = AgentFinishedHandler { eventContext ->
            with(context.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentRunError(InterceptContext) { eventContext ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentRunError(
        context: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentRunErrorContext) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) }

        existingHandler.agentRunErrorHandler = AgentRunErrorHandler { eventContext ->
            with(context.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * @param TFeature The type of feature this handler is associated with.
     * @param context The context containing details about the feature and its implementation.
     * @param handle A suspendable function that is executed during the agent's pre-close phase.
     *                The function receives the feature instance and the event context as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptAgentBeforeClosed(InterceptContext) { eventContext ->
     *     // Handle agent run before close event.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentBeforeClosed(
        context: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentBeforeCloseContext) -> Unit
    ) {
        val existingHandler = agentHandlers.getOrPut(context.feature.key) { AgentHandler(context.featureImpl) }

        existingHandler.agentBeforeCloseHandler = AgentBeforeCloseHandler { eventContext ->
            with(context.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarted(InterceptContext) {
     *     val strategyName = strategy.name
     *     logger.info("Strategy $strategyName has started execution")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyStarted(
        context: InterceptContext<TFeature>,
        handle: suspend (StrategyStartContext<TFeature>) -> Unit
    ) {
        val existingHandler = strategyHandlers
            .getOrPut(context.feature.key) { StrategyHandler(context.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${context.featureImpl::class}>, but get a handler of type <${context.feature.key}> instead. " +
                    "Skipping adding strategy started interceptor for feature."
            }
            return
        }

        existingHandler.strategyStartedHandler = StrategyStartedHandler { eventContext ->
            handle(eventContext)
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
     * pipeline.interceptStrategyFinished(InterceptContext) { result ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyFinished(
        context: InterceptContext<TFeature>,
        handle: suspend (StrategyFinishContext<TFeature>) -> Unit
    ) {
        val existingHandler = strategyHandlers.getOrPut(context.feature.key) { StrategyHandler(context.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (existingHandler as? StrategyHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${context.featureImpl::class}>, but get a handler of type <${context.feature.key}> instead. " +
                    "Skipping adding strategy finished interceptor for feature."
            }
            return
        }

        existingHandler.strategyFinishedHandler = StrategyFinishedHandler { eventContext ->
            handle(eventContext)
        }
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeLLMCall(InterceptContext) { eventContext ->
     *     logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeLLMCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: BeforeLLMCallContext) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(interceptContext.feature.key) { ExecuteLLMHandler() }

        existingHandler.beforeLLMCallHandler = BeforeLLMCallHandler { eventContext: BeforeLLMCallContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptAfterLLMCall(InterceptContext) { eventContext ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAfterLLMCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AfterLLMCallContext) -> Unit
    ) {
        val existingHandler = executeLLMHandlers.getOrPut(interceptContext.feature.key) { ExecuteLLMHandler() }

        existingHandler.afterLLMCallHandler = AfterLLMCallHandler { eventContext: AfterLLMCallContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts streaming operations before they begin to modify or log the streaming request.
     *
     * This method allows features to hook into the streaming pipeline before streaming starts,
     * enabling preprocessing, validation, or logging of streaming requests.
     *
     * @param interceptContext The context containing the feature and its implementation
     * @param handle The handler that processes before-stream events
     *
     * Example:
     * ```
     * pipeline.interceptBeforeStream(InterceptContext) { eventContext ->
     *     logger.info("About to start streaming with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptBeforeStream(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: BeforeStreamContext) -> Unit
    ) {
        val existingHandler = streamHandlers.getOrPut(interceptContext.feature.key) { StreamHandler() }

        existingHandler.beforeStreamHandler = BeforeStreamHandler { eventContext: BeforeStreamContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts stream frames as they are received during the streaming process.
     *
     * This method allows features to process individual stream frames in real-time,
     * enabling monitoring, transformation, or aggregation of streaming content.
     *
     * @param interceptContext The context containing the feature and its implementation
     * @param handle The handler that processes stream frame events
     *
     * Example:
     * ```
     * pipeline.interceptOnStreamFrame(InterceptContext) { eventContext ->
     *     logger.debug("Received stream frame: ${eventContext.streamFrame}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptOnStreamFrame(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: StreamFrameContext) -> Unit
    ) {
        val existingHandler = streamHandlers.getOrPut(interceptContext.feature.key) { StreamHandler() }

        existingHandler.streamFrameHandler = StreamFrameHandler { eventContext: StreamFrameContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * @param interceptContext The context containing the feature and its implementation
     * @param handle The handler that processes stream errors
     */
    public fun <TFeature : Any> interceptOnStreamError(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: StreamErrorContext) -> Unit
    ) {
        val existingHandler = streamHandlers.getOrPut(interceptContext.feature.key) { StreamHandler() }

        existingHandler.streamErrorHandler = StreamErrorHandler { eventContext: StreamErrorContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    /**
     * Intercepts streaming operations after they complete to perform post-processing or cleanup.
     *
     * This method allows features to hook into the streaming pipeline after streaming finishes,
     * enabling post-processing, cleanup, or final logging of the streaming session.
     *
     * @param interceptContext The context containing the feature and its implementation
     * @param handle The handler that processes after-stream events
     *
     * Example:
     * ```
     * pipeline.interceptAfterStream(InterceptContext) { eventContext ->
     *     logger.info("Streaming completed for run: ${eventContext.runId}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAfterStream(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AfterStreamContext) -> Unit
    ) {
        val existingHandler = streamHandlers.getOrPut(interceptContext.feature.key) { StreamHandler() }

        existingHandler.afterStreamHandler = AfterStreamHandler { eventContext: AfterStreamContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
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
     * pipeline.interceptToolCall(InterceptContext) { eventContext ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCall(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallHandler = ToolCallHandler { eventHandler: ToolCallContext ->
            with(interceptContext.featureImpl) { handle(eventHandler) }
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
     * pipeline.interceptToolValidationError(InterceptContext) { eventContext ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolValidationError(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolValidationErrorContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolValidationErrorHandler = ToolValidationErrorHandler { eventContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
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
     * pipeline.interceptToolCallFailure(InterceptContext) { eventContext ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCallFailure(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallFailureContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallFailureHandler = ToolCallFailureHandler { eventContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
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
     * pipeline.interceptToolCallResult(InterceptContext) { eventContext ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolCallResult(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolCallResultContext) -> Unit
    ) {
        val existingHandler = executeToolHandlers.getOrPut(interceptContext.feature.key) { ExecuteToolHandler() }

        existingHandler.toolCallResultHandler = ToolCallResultHandler { eventContext ->
            with(interceptContext.featureImpl) { handle(eventContext) }
        }
    }

    //endregion Interceptors
}
