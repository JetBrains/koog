package ai.koog.agents.core.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedHandler
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingHandler
import ai.koog.agents.core.feature.handler.agent.AgentContextHandler
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingHandler
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedHandler
import ai.koog.agents.core.feature.handler.agent.AgentEventHandler
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedHandler
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingHandler
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.llm.LLMCallEventHandler
import ai.koog.agents.core.feature.handler.tool.ToolExecutionEventHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyEventHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingEventHandler
import ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailureHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallHandler
import ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallResultHandler
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationErrorHandler
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
    protected val agentEventHandlers: MutableMap<AIAgentStorageKey<*>, AgentEventHandler<*>> = mutableMapOf()

    /**
     * Map of strategy handlers registered for different features.
     * Keys are feature storage keys, values are strategy handlers.
     */
    protected val strategyEventHandlers: MutableMap<AIAgentStorageKey<*>, StrategyEventHandler<*>> = mutableMapOf()

    /**
     * Map of agent context handlers registered for different features.
     * Keys are feature storage keys, values are agent context handlers.
     */
    protected val agentContextHandler: MutableMap<AIAgentStorageKey<*>, AgentContextHandler<*>> = mutableMapOf()

    /**
     * Map of tool execution handlers registered for different features.
     * Keys are feature storage keys, values are tool execution handlers.
     */
    protected val toolExecutionEventHandlers: MutableMap<AIAgentStorageKey<*>, ToolExecutionEventHandler> = mutableMapOf()

    /**
     * Map of LLM execution handlers registered for different features.
     * Keys are feature storage keys, values are LLM execution handlers.
     */
    protected val llmCallEventHandlers: MutableMap<AIAgentStorageKey<*>, LLMCallEventHandler> = mutableMapOf()

    /**
     * Map of feature storage keys to their stream handlers.
     * These handlers manage the streaming lifecycle events (before, during, and after streaming).
     */
    protected val llmStreamingEventHandlers: MutableMap<AIAgentStorageKey<*>, LLMStreamingEventHandler> = mutableMapOf()

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
    public suspend fun <TInput, TOutput> onAgentStarting(
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        agentEventHandlers.values.forEach { handler ->
            val eventContext =
                AgentStartingContext(
                    agent = agent,
                    runId = runId,
                    feature = handler.feature,
                    context = context
                )
            handler.handleAgentStartingUnsafe(eventContext)
        }
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     */
    public suspend fun onAgentCompleted(
        agentId: String,
        runId: String,
        result: Any?,
        resultType: KType,
    ) {
        val eventContext =
            AgentCompletedContext(agentId = agentId, runId = runId, result = result, resultType = resultType)
        agentEventHandlers.values.forEach { handler -> handler.agentCompletedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The exception that was thrown during agent execution
     */
    public suspend fun onAgentExecutionFailed(
        agentId: String,
        runId: String,
        throwable: Throwable
    ) {
        val eventContext = AgentExecutionFailedContext(agentId = agentId, runId = runId, throwable = throwable)
        agentEventHandlers.values.forEach { handler -> handler.agentExecutionFailedHandler.handle(eventContext) }
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public suspend fun onAgentClosing(
        agentId: String
    ) {
        val eventContext = AgentClosingContext(agentId = agentId)
        agentEventHandlers.values.forEach { handler -> handler.agentClosingHandler.handle(eventContext) }
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
    public suspend fun onAgentEnvironmentTransforming(
        strategy: AIAgentGraphStrategy<*, *>,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        return agentEventHandlers.values.fold(baseEnvironment) { environment, handler ->
            val eventContext = AgentEnvironmentTransformingContext(strategy = strategy, agent = agent, feature = handler.feature)
            handler.transformEnvironmentUnsafe(eventContext, environment)
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
    public suspend fun onStrategyStarting(strategy: AIAgentStrategy<*, *, *>, context: AIAgentContext) {
        strategyEventHandlers.values.forEach { handler ->
            val eventContext = StrategyStartingContext(
                runId = context.runId,
                strategy = strategy,
                feature = handler.feature,
                context = context
            )
            handler.handleStrategyStartingUnsafe(eventContext)
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
    public suspend fun onStrategyCompleted(
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType,
    ) {
        strategyEventHandlers.values.forEach { handler ->
            val eventContext = StrategyCompletedContext(
                runId = context.runId,
                strategy = strategy,
                feature = handler.feature,
                result = result,
                resultType = resultType
            )
            handler.handleStrategyCompletedUnsafe(eventContext)
        }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param prompt The prompt that will be sent to the language model
     * @param tools The list of tool descriptors available for the LLM call
     * @param model The language model instance that will process the request
     */
    public suspend fun onLLMCallStarting(runId: String, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>) {
        val eventContext = LLMCallStartingContext(runId, prompt, model, tools)
        llmCallEventHandlers.values.forEach { handler -> handler.llmCallStartingHandler.handle(eventContext) }
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
    public suspend fun onLLMCallCompleted(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult? = null,
    ) {
        val eventContext = LLMCallCompletedContext(runId, prompt, model, tools, responses, moderationResponse)
        llmCallEventHandlers.values.forEach { handler -> handler.llmCallCompletedHandler.handle(eventContext) }
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
    public suspend fun onToolExecutionStarting(runId: String, toolCallId: String?, tool: Tool<*, *>, toolArgs: ToolArgs) {
        val eventContext = ToolExecutionStartingContext(runId, toolCallId, tool, toolArgs)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolCallHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool for which validation failed
     * @param toolArgs The arguments that failed validation
     * @param error The validation error message
     */
    public suspend fun onToolValidationFailed(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        error: String
    ) {
        val eventContext =
            ToolValidationFailedContext(runId, toolCallId, tool, toolArgs, error)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that failed
     * @param toolArgs The arguments provided to the tool
     * @param throwable The exception that caused the failure
     */
    public suspend fun onToolExecutionFailed(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        throwable: Throwable
    ) {
        val eventContext = ToolExecutionFailedContext(runId, toolCallId, tool, toolArgs, throwable)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolCallFailureHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param runId The unique identifier for the current run.
     * @param tool The tool that was called
     * @param toolArgs The arguments that were provided to the tool
     * @param result The result produced by the tool, or null if no result was produced
     */
    public suspend fun onToolExecutionCompleted(
        runId: String,
        toolCallId: String?,
        tool: Tool<*, *>,
        toolArgs: ToolArgs,
        result: ToolResult?
    ) {
        val eventContext = ToolExecutionCompletedContext(runId, toolCallId, tool, toolArgs, result)
        toolExecutionEventHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(eventContext) }
    }

    //endregion Trigger Tool Call Handlers

    //region Trigger LLM Streaming

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
    public suspend fun onLLMStreamingStarting(runId: String, prompt: Prompt, model: LLModel, tools: List<ToolDescriptor>) {
        val eventContext = LLMStreamingStartingContext(runId, prompt, model, tools)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingStartingHandler.handle(eventContext) }
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
    public suspend fun onLLMStreamingFrameReceived(runId: String, streamFrame: StreamFrame) {
        val eventContext = LLMStreamingFrameReceivedContext(runId, streamFrame)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingFrameReceivedHandler.handle(eventContext) }
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
    public suspend fun onLLMStreamingFailed(runId: String, throwable: Throwable) {
        val eventContext = LLMStreamingFailedContext(runId, throwable)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingFailedHandler.handle(eventContext) }
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
    public suspend fun onLLMStreamingCompleted(
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        val eventContext = LLMStreamingCompletedContext(runId, prompt, model, tools)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingCompletedHandler.handle(eventContext) }
    }

    //endregion Trigger LLM Streaming

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
     * @param interceptContext The context of the feature being intercepted, providing access to the feature key and implementation
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
        interceptContext: InterceptContext<TFeature>,
        transform: suspend AgentEnvironmentTransformingContext<TFeature>.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        @Suppress("UNCHECKED_CAST")
        val handler: AgentEventHandler<TFeature> =
            agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) } as? AgentEventHandler<TFeature>
                ?: return

        handler.agentEnvironmentTransformingHandler = AgentEnvironmentTransformingHandler(
            function = createConditionalHandler(interceptContext, transform)
        )
    }

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     *
     * @param handle The handler that processes agent creation events
     *
     * Example:
     * ```
     * pipeline.interceptAgentStarting(InterceptContext) {
     *     readStages { stages ->
     *         // Inspect agent stages
     *     }
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (AgentStartingContext<TFeature>) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val handler: AgentEventHandler<TFeature> =
            agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) } as? AgentEventHandler<TFeature>
                ?: return

        handler.agentStartingHandler = AgentStartingHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentCompleted(interceptContext) { eventContext ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentCompletedContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) }
        handler.agentCompletedHandler = AgentCompletedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentExecutionFailed(interceptContext) { eventContext ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentExecutionFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) }
        handler.agentExecutionFailedHandler = AgentExecutionFailedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * @param TFeature The type of feature this handler is associated with.
     * @param interceptContext The context containing details about the feature and its implementation.
     * @param handle A suspendable function that is executed during the agent's pre-close phase.
     *                The function receives the feature instance and the event context as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptAgentClosing(interceptContext) { eventContext ->
     *     // Handle agent run before close event.
     * }
     * ```
     */
    public fun <TFeature : Any> interceptAgentClosing(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: AgentClosingContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(interceptContext.feature.key) { AgentEventHandler(interceptContext.featureImpl) }
        handler.agentClosingHandler = AgentClosingHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarting(interceptContext) { event ->
     *     val strategyName = event.strategy.name
     *     logger.info("Strategy $strategyName has started execution")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (StrategyStartingContext<TFeature>) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(interceptContext.feature.key) { StrategyEventHandler(interceptContext.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (handler as? StrategyEventHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${interceptContext.featureImpl::class}>, but get a handler of type <${interceptContext.feature.key}> instead. " +
                    "Skipping adding strategy started interceptor for feature."
            }
            return
        }

        handler.strategyStartingHandler = StrategyStartingHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * @param handle A suspend function that processes the completion of a strategy, accepting the strategy name
     *               and its result as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptStrategyCompleted(interceptContext) { event ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptStrategyCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend (StrategyCompletedContext<TFeature>) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(interceptContext.feature.key) { StrategyEventHandler(interceptContext.featureImpl) }

        @Suppress("UNCHECKED_CAST")
        if (handler as? StrategyEventHandler<TFeature> == null) {
            logger.debug {
                "Expected to get an agent handler for feature of type <${interceptContext.featureImpl::class}>, " +
                    "but get a handler of type <${interceptContext.feature.key}> instead. " +
                    "Skipping adding strategy finished interceptor for feature."
            }
            return
        }

        handler.strategyCompletedHandler = StrategyCompletedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptLLMCallStarting(interceptContext) { eventContext ->
     *     logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMCallStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMCallStartingContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(interceptContext.feature.key) { LLMCallEventHandler() }
        handler.llmCallStartingHandler = LLMCallStartingHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptLLMCallCompleted(interceptContext) { eventContext ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMCallCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMCallCompletedContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(interceptContext.feature.key) { LLMCallEventHandler() }

        handler.llmCallCompletedHandler = LLMCallCompletedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
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
     * pipeline.interceptLLMStreamingStarting(interceptContext) { eventContext ->
     *     logger.info("About to start streaming with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMStreamingStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(interceptContext.feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingStartingHandler = LLMStreamingStartingHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
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
     * pipeline.interceptLLMStreamingFrameReceived(interceptContext) { eventContext ->
     *     logger.debug("Received stream frame: ${eventContext.streamFrame}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMStreamingFrameReceived(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(interceptContext.feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingFrameReceivedHandler = LLMStreamingFrameReceivedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * @param interceptContext The context containing the feature and its implementation
     * @param handle The handler that processes stream errors
     */
    public fun <TFeature : Any> interceptLLMStreamingFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(interceptContext.feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingFailedHandler = LLMStreamingFailedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
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
     * pipeline.interceptLLMStreamingCompleted(interceptContext) { eventContext ->
     *     logger.info("Streaming completed for run: ${eventContext.runId}")
     * }
     * ```
     */
    public fun <TFeature : Any> interceptLLMStreamingCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(interceptContext.feature.key) { LLMStreamingEventHandler() }
        handler.llmStreamingCompletedHandler = LLMStreamingCompletedHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     *
     * @param handle A suspend lambda function that processes tool calls, taking the tool, and its arguments as parameters.
     *
     * Example:
     * ```
     * pipeline.interceptToolExecutionStarting(interceptContext) { eventContext ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolExecutionStarting(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolExecutionStartingContext) -> Unit
    ) {
        val handler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }
        handler.toolCallHandler = ToolCallHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * @param handle A suspendable lambda function that will be invoked when a tool validation error occurs.
     *        The lambda provides the tool's stage, tool instance, tool arguments, and the value that caused the validation error.
     *
     * Example:
     * ```
     * pipeline.interceptToolValidationFailed(interceptContext) { eventContext ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolValidationFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolValidationFailedContext) -> Unit
    ) {
        val handler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }
        handler.toolValidationErrorHandler = ToolValidationErrorHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * @param handle A suspend function that is invoked when a tool call fails. It provides the stage,
     *               the tool, the tool arguments, and the throwable that caused the failure.
     *
     * Example:
     * ```
     * pipeline.interceptToolExecutionFailed(interceptContext) { eventContext ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public fun <TFeature : Any> interceptToolExecutionFailed(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolExecutionFailedContext) -> Unit
    ) {
        val handler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }
        handler.toolCallFailureHandler = ToolCallFailureHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
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
    public fun <TFeature : Any> interceptToolExecutionCompleted(
        interceptContext: InterceptContext<TFeature>,
        handle: suspend TFeature.(eventContext: ToolExecutionCompletedContext) -> Unit
    ) {
        val handler = toolExecutionEventHandlers.getOrPut(interceptContext.feature.key) { ToolExecutionEventHandler() }
        handler.toolCallResultHandler = ToolCallResultHandler(
            function = createConditionalHandler(interceptContext, handle)
        )
    }

    //endregion Interceptors

    //region Private Methods

    protected inline fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        interceptContext: InterceptContext<*>,
        crossinline handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = handler@{ eventContext ->
        val featureConfig = registeredFeatures[interceptContext.feature.key]

        if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
            return@handler
        }

        handle(eventContext)
    }

    protected inline fun <TFeature : Any, TContext : AgentLifecycleEventContext> createConditionalHandler(
        interceptContext: InterceptContext<TFeature>,
        crossinline handle: suspend TFeature.(TContext) -> Unit
    ): suspend (TContext) -> Unit = handler@{ eventContext ->
        val featureConfig = registeredFeatures[interceptContext.feature.key]

        if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
            return@handler
        }

        with(interceptContext.featureImpl) { handle(eventContext) }
    }

    protected inline fun <TFeature : Any> createConditionalHandler(
        interceptContext: InterceptContext<TFeature>,
        crossinline handle: suspend AgentEnvironmentTransformingContext<TFeature>.(AIAgentEnvironment) -> AIAgentEnvironment
    ): suspend (AgentEnvironmentTransformingContext<TFeature>, AIAgentEnvironment) -> AIAgentEnvironment = handler@{ eventContext, env ->
        val featureConfig = registeredFeatures[interceptContext.feature.key]

        if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
            return@handler env
        }

        eventContext.handle(env)
    }

    protected fun FeatureConfig.isAccepted(eventContext: AgentLifecycleEventContext): Boolean {
        return this.eventFilter.invoke(eventContext)
    }

    //endregion Private Methods
}
