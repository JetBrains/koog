package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.config.FeatureSystemVariables
import ai.koog.agents.core.feature.debugger.Debugger
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentClosingHandler
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedHandler
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingHandler
import ai.koog.agents.core.feature.handler.agent.AgentEventHandler
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedHandler
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallEventHandler
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyEventHandler
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingEventHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedHandler
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallEventHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailureHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallResultHandler
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationErrorHandler
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.system.getEnvironmentVariableOrNull
import ai.koog.agents.core.system.getVMOptionOrNull
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.safeCast

/**
 * Pipeline for AI agent features that provides interception points for various agent lifecycle events.
 *
 * The pipeline allows features to:
 * - Be installed into agent contexts
 * - Intercept agent creation and environment transformation
 * - Intercept strategy execution before and after it happens
 * - Intercept node execution before and after it happens
 * - Intercept LLM calls before and after they happen
 * - Intercept tool calls before and after they happen
 *
 * This pipeline serves as the central mechanism for extending and customizing agent behavior
 * through a flexible interception system. Features can be installed with custom configurations
 * and can hook into different stages of the agent's execution lifecycle.
 *
 * @property clock Clock instance for time-related operations
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

    /**
     * Represents configured and installed agent feature implementation along with its configuration.
     * @param featureImpl The feature implementation
     * @param featureConfig The feature configuration
     */
    @Suppress("RedundantVisibilityModifier") // have to put public here, explicitApi requires it
    private class RegisteredFeature(
        public val featureImpl: Any,
        public val featureConfig: FeatureConfig
    )

    /**
     * Map of registered features and their configurations.
     * Keys are feature storage keys, values are feature configurations.
     */
    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, RegisteredFeature> = mutableMapOf()

    /**
     * Set of system features that are always defined by the framework.
     */
    @OptIn(ExperimentalAgentsApi::class)
    private val systemFeatures: Set<AIAgentStorageKey<*>> = setOf(
        Debugger.key
    )

    /**
     * Map of agent handlers registered for different features.
     * Keys are feature storage keys, values are agent handlers.
     */
    protected val agentEventHandlers: MutableMap<AIAgentStorageKey<*>, AgentEventHandler> = mutableMapOf()

    /**
     * Map of strategy handlers registered for different features.
     * Keys are feature storage keys, values are strategy handlers.
     */
    protected val strategyEventHandlers: MutableMap<AIAgentStorageKey<*>, StrategyEventHandler> = mutableMapOf()

    /**
     * Map of tool execution handlers registered for different features.
     * Keys are feature storage keys, values are tool execution handlers.
     */
    protected val toolCallEventHandlers: MutableMap<AIAgentStorageKey<*>, ToolCallEventHandler> = mutableMapOf()

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

    /**
     * Retrieves a feature implementation from the current pipeline using the specified [feature], if it is registered.
     *
     * @param TFeature A feature implementation type.
     * @param feature A feature to fetch.
     * @param featureClass The [KClass] of the feature to be retrieved.
     * @return The feature associated with the provided key, or null if no matching feature is found.
     * @throws IllegalArgumentException if the specified [featureClass] does not correspond to a registered feature.
     */
    public fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature? {
        val featureImpl = registeredFeatures[feature.key]?.featureImpl ?: return null

        return featureClass.safeCast(featureImpl)
            ?: throw IllegalArgumentException(
                "Feature ${feature.key} is found, but it is not of the expected type.\n" +
                    "Expected type: ${featureClass.simpleName}\n" +
                    "Actual type: ${featureImpl::class.simpleName}"
            )
    }

    protected fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: AIAgentStorageKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl,
    ) {
        registeredFeatures[featureKey] = RegisteredFeature(featureImpl, featureConfig)
    }

    protected suspend fun uninstall(
        featureKey: AIAgentStorageKey<*>
    ) {
        registeredFeatures
            .filter { (key, _) -> key == featureKey }
            .forEach { (key, registeredFeature) ->
                registeredFeature.featureConfig.messageProcessors.forEach { provider -> provider.close() }
                registeredFeatures.remove(key)
            }
    }

    //region Internal Handlers

    /**
     * Prepares the feature by initializing all the associated message processors defined in the feature configuration.
     *
     * @param featureConfig The configuration object containing the list of message processors to be initialized.
     */
    internal suspend fun prepareFeature(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { processor ->
            logger.debug { "Start preparing processor: ${processor::class.simpleName}" }
            processor.initialize()
            logger.debug { "Finished preparing processor: ${processor::class.simpleName}" }
        }
    }

    /**
     * Prepares features by initializing their respective message processors.
     */
    internal suspend fun prepareAllFeatures() {
        // Install system features (if exist)
        installFeaturesFromSystemConfig()

        // Prepare features
        registeredFeatures.values.forEach { featureConfig ->
            prepareFeature(featureConfig.featureConfig)
        }
    }

    /**
     * Closes all message processors associated with the provided feature by feature configuration.
     *
     * @param featureConfig The configuration object containing the message processors to be closed.
     */
    internal suspend fun closeFeatureMessageProcessors(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { provider ->
            logger.trace { "Start closing feature processor: ${featureConfig::class.simpleName}" }
            provider.close()
            logger.trace { "Finished closing feature processor: ${featureConfig::class.simpleName}" }
        }
    }

    /**
     * Closes all feature stream providers.
     *
     * This internal method properly shuts down all message processors of registered features,
     * ensuring resources are released appropriately.
     */
    internal suspend fun closeAllFeaturesMessageProcessors() {
        registeredFeatures.values.forEach { registerFeature ->
            closeFeatureMessageProcessors(registerFeature.featureConfig)
        }
    }

    //endregion Internal Handlers

    //region Trigger Agent Handlers

    /**
     * Notifies all registered handlers that an agent has started execution.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param runId The unique identifier for the agent run
     * @param agent The agent instance for which the execution has started
     * @param context The context of the agent execution, providing access to the agent environment and context features
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun <TInput, TOutput> onAgentStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        val eventContext = AgentStartingContext(executionInfo, agent, runId, context)
        agentEventHandlers.values.forEach { handler ->
            handler.handleAgentStarting(eventContext)
        }
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     */
    public suspend fun onAgentCompleted(
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        result: Any?
    ) {
        val eventContext = AgentCompletedContext(executionInfo, agentId, runId, result)
        agentEventHandlers.values.forEach { handler -> handler.agentCompletedHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The [Throwable] exception instance that was thrown during agent execution
     */
    public suspend fun onAgentExecutionFailed(
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        throwable: Throwable
    ) {
        val eventContext = AgentExecutionFailedContext(executionInfo, agentId, runId, throwable)
        agentEventHandlers.values.forEach { handler -> handler.agentExecutionFailedHandler.handle(eventContext) }
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public suspend fun onAgentClosing(
        executionInfo: AgentExecutionInfo,
        agentId: String
    ) {
        val eventContext = AgentClosingContext(executionInfo, agentId)
        agentEventHandlers.values.forEach { handler -> handler.agentClosingHandler.handle(eventContext) }
    }

    /**
     * Transforms the agent environment by applying all registered environment transformers.
     *
     * This method allows features to modify or enhance the agent's environment before it starts execution.
     * Each registered handler can apply its own transformations to the environment in sequence.
     *
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agent The agent instance for which the environment is being transformed
     * @param baseEnvironment The initial environment to be transformed
     * @return The transformed environment after all handlers have been applied
     */
    public suspend fun onAgentEnvironmentTransforming(
        executionInfo: AgentExecutionInfo,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        val eventContext = AgentEnvironmentTransformingContext(executionInfo, agent)
        return agentEventHandlers.values.fold(baseEnvironment) { environment, handler ->
            handler.transformEnvironment(eventContext, environment)
        }
    }

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param executionInfo The execution information for the strategy event
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyStarting(
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext
    ) {
        val eventContext = StrategyStartingContext(executionInfo, strategy, context)
        strategyEventHandlers.values.forEach { handler -> handler.handleStrategyStarting(eventContext) }
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param executionInfo The execution information for the strategy event
     * @param strategy The strategy that has finished execution
     * @param context The context of the strategy execution
     * @param result The result produced by the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyCompleted(
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType,
    ) {
        val eventContext =
            StrategyCompletedContext(executionInfo, strategy, context, result, resultType)
        strategyEventHandlers.values.forEach { handler -> handler.handleStrategyCompleted(eventContext) }
    }

    //endregion Trigger Strategy Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param executionInfo The execution information for the LLM call event
     * @param runId The unique identifier for the current run.
     * @param callId The unique identifier for the LLM call
     * @param prompt The prompt that will be sent to the language model
     * @param model The language model instance that will process the request
     * @param tools The list of tool descriptors available for the LLM call
     */
    public suspend fun onLLMCallStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        callId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        val eventContext = LLMCallStartingContext(executionInfo, runId, callId, prompt, model, tools)
        llmCallEventHandlers.values.forEach { handler -> handler.llmCallStartingHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param executionInfo The execution information for the LLM call event
     * @param runId Identifier for the current run.
     * @param callId Identifier for the current LLM call.
     * @param prompt The prompt that was sent to the language model
     * @param model The language model instance that processed the request
     * @param tools The list of tool descriptors that were available for the LLM call
     * @param responses The response messages received from the language model
     * @param moderationResponse The moderation response, if any, received from the language model
     */
    public suspend fun onLLMCallCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        callId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult? = null,
    ) {
        val eventContext = LLMCallCompletedContext(executionInfo, runId, callId, prompt, model, tools, responses, moderationResponse)
        llmCallEventHandlers.values.forEach { handler -> handler.llmCallCompletedHandler.handle(eventContext) }
    }

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

    /**
     * Notifies all registered tool handlers when a tool is called.
     *
     * @param executionInfo The execution information for the tool call event
     * @param runId The unique identifier for the current run.
     * @param toolCallId The unique identifier for the current tool call.
     * @param toolName The tool name that is being called
     * @param toolArgs The arguments provided to the tool
     */
    public suspend fun onToolCallStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
    ) {
        val eventContext = ToolCallStartingContext(executionInfo, runId, toolCallId, toolName, toolArgs)
        toolCallEventHandlers.values.forEach { handler -> handler.toolCallHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param executionInfo The execution information for the tool call event;
     * @param runId The unique identifier for the current run;
     * @param toolCallId The unique identifier for the current tool call;
     * @param toolName The name of the tool for which validation failed;
     * @param toolArgs The arguments that failed validation;
     * @param toolDescription The description of the tool that was called;
     * @param message The validation error message;
     * @param error The [AIAgentError] validation error.
     */
    public suspend fun onToolValidationFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        message: String,
        error: AIAgentError,
    ) {
        val eventContext = ToolValidationFailedContext(executionInfo, runId, toolCallId, toolName, toolArgs, toolDescription, message, error)
        toolCallEventHandlers.values.forEach { handler -> handler.toolValidationErrorHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param executionInfo The execution information for the tool call agent event
     * @param runId The unique identifier for the current run;
     * @param toolCallId The unique identifier for the current tool call;
     * @param toolName The tool name that was called;
     * @param toolArgs The arguments provided to the tool;
     * @param toolDescription The description of the tool that was called;
     * @param message A message describing the failure.
     * @param error The [AIAgentError] that caused the failure.
     */
    public suspend fun onToolCallFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        message: String,
        error: AIAgentError?
    ) {
        val eventContext = ToolCallFailedContext(executionInfo, runId, toolCallId, toolName, toolArgs, toolDescription, message, error)
        toolCallEventHandlers.values.forEach { handler -> handler.toolCallFailureHandler.handle(eventContext) }
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param executionInfo The execution information for the tool call agent event
     * @param runId The unique identifier for the current run;
     * @param toolCallId The unique identifier for the current tool call;
     * @param toolName The tool name that was called;
     * @param toolArgs The arguments that were provided to the tool;
     * @param toolDescription The description of the tool that was called;
     * @param toolResult The result produced by the tool, or null if no result was produced.
     */
    public suspend fun onToolCallCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolArgs: JsonObject,
        toolDescription: String?,
        toolResult: JsonElement?
    ) {
        val eventContext = ToolCallCompletedContext(executionInfo, runId, toolCallId, toolName, toolArgs, toolDescription, toolResult)
        toolCallEventHandlers.values.forEach { handler -> handler.toolCallResultHandler.handle(eventContext) }
    }

    //endregion Trigger Tool Call Handlers

    //region Trigger LLM Streaming

    /**
     * Invoked before streaming from a language model begins.
     *
     * This method notifies all registered stream handlers that streaming is about to start,
     * allowing them to perform preprocessing or logging operations.
     *
     * @param executionInfo The execution information for the LLM streaming event
     * @param runId The unique identifier for this streaming session
     * @param callId The unique identifier for this LLM call
     * @param prompt The prompt being sent to the language model
     * @param model The language model being used for streaming
     * @param tools The list of available tool descriptors for this streaming session
     */
    public suspend fun onLLMStreamingStarting(
        executionInfo: AgentExecutionInfo,
        runId: String,
        callId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        val eventContext = LLMStreamingStartingContext(executionInfo, runId, callId, prompt, model, tools)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingStartingHandler.handle(eventContext) }
    }

    /**
     * Invoked when a stream frame is received during the streaming process.
     *
     * This method notifies all registered stream handlers about each incoming stream frame,
     * allowing them to process, transform, or aggregate the streaming content in real-time.
     *
     * @param executionInfo The execution information for the LLM streaming event
     * @param runId The unique identifier for this streaming session
     * @param callId The unique identifier for this LLM call
     * @param streamFrame The individual stream frame containing partial response data
     */
    public suspend fun onLLMStreamingFrameReceived(
        executionInfo: AgentExecutionInfo,
        runId: String,
        callId: String,
        streamFrame: StreamFrame
    ) {
        val eventContext = LLMStreamingFrameReceivedContext(executionInfo, runId, callId, streamFrame)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingFrameReceivedHandler.handle(eventContext) }
    }

    /**
     * Invoked if an error occurs during the streaming process.
     *
     * This method notifies all registered stream handlers about the streaming error,
     * allowing them to handle or log the error.
     *
     * @param executionInfo The execution information for the LLM streaming event
     * @param runId The unique identifier for this streaming session
     * @param callId The unique identifier for this LLM call
     * @param throwable The exception that occurred during streaming, if applicable
     */
    public suspend fun onLLMStreamingFailed(
        executionInfo: AgentExecutionInfo,
        runId: String,
        callId: String,
        throwable: Throwable
    ) {
        val eventContext = LLMStreamingFailedContext(executionInfo, runId, callId, throwable)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingFailedHandler.handle(eventContext) }
    }

    /**
     * Invoked after streaming from a language model completes.
     *
     * This method notifies all registered stream handlers that streaming has finished,
     * allowing them to perform post-processing, cleanup, or final logging operations.
     *
     * @param executionInfo The execution information for the LLM streaming event
     * @param runId The unique identifier for this streaming session
     * @param callId The unique identifier for this LLM call
     * @param prompt The prompt that was sent to the language model
     * @param model The language model that was used for streaming
     * @param tools The list of tool descriptors that were available for this streaming session
     */
    public suspend fun onLLMStreamingCompleted(
        executionInfo: AgentExecutionInfo,
        runId: String,
        callId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ) {
        val eventContext = LLMStreamingCompletedContext(executionInfo, runId, callId, prompt, model, tools)
        llmStreamingEventHandlers.values.forEach { handler -> handler.llmStreamingCompletedHandler.handle(eventContext) }
    }

    //endregion Trigger LLM Streaming

    //region Interceptors

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This method registers a transformer function that will be called when an agent environment
     * is being created, allowing the feature to customize the environment based on the agent context.
     *
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
    public fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        transform: suspend AgentEnvironmentTransformingContext.(AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        val handler: AgentEventHandler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentEnvironmentTransformingHandler = AgentEnvironmentTransformingHandler(
            function = createConditionalHandler(feature, transform)
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
    public fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        val handler: AgentEventHandler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentStartingHandler = AgentStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     *
     * @param handle A suspend function providing custom logic to execute when the agent completes,
     *
     * Example:
     * ```
     * pipeline.interceptAgentCompleted(feature) { eventContext ->
     *     // Handle the completion result here, using the strategy name and the result.
     * }
     * ```
     */
    public fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentCompletedHandler = AgentCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     *
     * @param handle A suspend function providing custom logic to execute when an error occurs,
     *
     * Example:
     * ```
     * pipeline.interceptAgentExecutionFailed(feature) { eventContext ->
     *     // Handle the error here, using the strategy name and the exception that occurred.
     * }
     * ```
     */
    public fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentExecutionFailedHandler = AgentExecutionFailedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspendable function that is executed during the agent's pre-close phase.
     *
     * Example:
     * ```
     * pipeline.interceptAgentClosing(feature) { eventContext ->
     *     // Handle agent run before close event.
     * }
     * ```
     */
    public fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    ) {
        val handler = agentEventHandlers.getOrPut(feature.key) { AgentEventHandler() }

        handler.agentClosingHandler = AgentClosingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the start of a strategy, accepting the strategy context
     *
     * Example:
     * ```
     * pipeline.interceptStrategyStarting(feature) { event ->
     *     val strategyName = event.strategy.name
     *     logger.info("Strategy $strategyName has started execution")
     * }
     * ```
     */
    public fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(feature.key) { StrategyEventHandler() }

        handler.strategyStartingHandler = StrategyStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that processes the completion of a strategy.
     *
     * Example:
     * ```
     * pipeline.interceptStrategyCompleted(feature) { event ->
     *     // Handle the completion of the strategy here
     * }
     * ```
     */
    public fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        val handler = strategyEventHandlers.getOrPut(feature.key) { StrategyEventHandler() }

        handler.strategyCompletedHandler = StrategyCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts LLM calls before they are made to modify or log the prompt.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptLLMCallStarting(feature) { eventContext ->
     *     logger.info("About to make LLM call with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(feature.key) { LLMCallEventHandler() }

        handler.llmCallStartingHandler = LLMCallStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-LLM-call events
     *
     * Example:
     * ```
     * pipeline.interceptLLMCallCompleted(feature) { eventContext ->
     *     // Process or analyze the response
     * }
     * ```
     */
    public fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        val handler = llmCallEventHandlers.getOrPut(feature.key) { LLMCallEventHandler() }

        handler.llmCallCompletedHandler = LLMCallCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts streaming operations before they begin to modify or log the streaming request.
     *
     * This method allows features to hook into the streaming pipeline before streaming starts,
     * enabling preprocessing, validation, or logging of streaming requests.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes before-stream events
     *
     * Example:
     * ```
     * pipeline.interceptLLMStreamingStarting(feature) { eventContext ->
     *     logger.info("About to start streaming with prompt: ${eventContext.prompt.messages.last().content}")
     * }
     * ```
     */
    public fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingStartingHandler = LLMStreamingStartingHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts stream frames as they are received during the streaming process.
     *
     * This method allows features to process individual stream frames in real-time,
     * enabling monitoring, transformation, or aggregation of streaming content.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes stream frame events
     *
     * Example:
     * ```
     * pipeline.interceptLLMStreamingFrameReceived(feature) { eventContext ->
     *     logger.debug("Received stream frame: ${eventContext.streamFrame}")
     * }
     * ```
     */
    public fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingFrameReceivedHandler = LLMStreamingFrameReceivedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes stream errors
     */
    public fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingFailedHandler = LLMStreamingFailedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts streaming operations after they complete to perform post-processing or cleanup.
     *
     * This method allows features to hook into the streaming pipeline after streaming finishes,
     * enabling post-processing, cleanup, or final logging of the streaming session.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes after-stream events
     *
     * Example:
     * ```
     * pipeline.interceptLLMStreamingCompleted(feature) { eventContext ->
     *     logger.info("Streaming completed for run: ${eventContext.runId}")
     * }
     * ```
     */
    public fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        val handler = llmStreamingEventHandlers.getOrPut(feature.key) { LLMStreamingEventHandler() }

        handler.llmStreamingCompletedHandler = LLMStreamingCompletedHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts and handles tool calls for the specified feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend lambda function that processes tool calls.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallStarting(feature) { eventContext ->
     *    // Process or log the tool call
     * }
     * ```
     */
    public fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolCallHandler = ToolCallHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspendable lambda function that will be invoked when a tool validation error occurs.
     *
     * Example:
     * ```
     * pipeline.interceptToolValidationFailed(feature) { eventContext ->
     *     // Handle the tool validation error here
     * }
     * ```
     */
    public fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolValidationErrorHandler = ToolValidationErrorHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspend function that is invoked when a tool call fails.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallFailed(feature) { eventContext ->
     *     // Handle the tool call failure here
     * }
     * ```
     */
    public fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolCallFailureHandler = ToolCallFailureHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     *
     * @param feature The feature associated with this handler.
     * @param handle A suspending function that defines the behavior to execute when a tool call result is intercepted.
     *
     * Example:
     * ```
     * pipeline.interceptToolCallCompleted(feature) { eventContext ->
     *     // Handle the tool call result here
     * }
     * ```
     */
    public fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        val handler = toolCallEventHandlers.getOrPut(feature.key) { ToolCallEventHandler() }

        handler.toolCallResultHandler = ToolCallResultHandler(
            function = createConditionalHandler(feature, handle)
        )
    }

    //endregion Interceptors

    //region Deprecated Interceptors

    /**
     * Intercepts on before an agent started to modify or enhance the agent.
     */
    @Deprecated(
        message = "Please use interceptAgentStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentStarting(feature, handle)",
            imports = arrayOf("ai.koog.agents.core.feature.handler.agent.AgentStartingContext")
        )
    )
    public fun interceptBeforeAgentStarted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        interceptAgentStarting(feature, handle)
    }

    /**
     * Intercepts the completion of an agent's operation and assigns a custom handler to process the result.
     */
    @Deprecated(
        message = "Please use interceptAgentCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.agent.AgentCompletedContext"
            )
        )
    )
    public fun interceptAgentFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        interceptAgentCompleted(feature, handle)
    }

    /**
     * Intercepts and handles errors occurring during the execution of an AI agent's strategy.
     */
    @Deprecated(
        message = "Please use interceptAgentExecutionFailed instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentExecutionFailed(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext"
            )
        )
    )
    public fun interceptAgentRunError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentExecutionFailedContext) -> Unit
    ) {
        interceptAgentExecutionFailed(feature, handle)
    }

    /**
     * Intercepts and sets a handler to be invoked before an agent is closed.
     */
    @Deprecated(
        message = "Please use interceptAgentClosing instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptAgentClosing(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.agent.AgentClosingContext"
            )
        )
    )
    public fun interceptAgentBeforeClose(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentClosingContext) -> Unit
    ) {
        interceptAgentClosing(feature, handle)
    }

    /**
     * Intercepts strategy started event to perform actions when an agent strategy begins execution.
     */
    @Deprecated(
        message = "Please use interceptStrategyStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptStrategyStarting(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext"
            )
        )
    )
    public fun interceptStrategyStart(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        interceptStrategyStarting(feature, handle)
    }

    /**
     * Sets up an interceptor to handle the completion of a strategy for the given feature.
     */
    @Deprecated(
        message = "Please use interceptStrategyCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptStrategyCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext"
            )
        )
    )
    public fun interceptStrategyFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        interceptStrategyCompleted(feature, handle)
    }

    /**
     * Intercepts LLM calls before they are made (deprecated name).
     */
    @Deprecated(
        message = "Please use interceptLLMCallStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptLLMCallStarting(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext"
            )
        )
    )
    public fun interceptBeforeLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        interceptLLMCallStarting(feature, handle)
    }

    /**
     * Intercepts LLM calls after they are made to process or log the response.
     */
    @Deprecated(
        message = "Please use interceptLLMCallCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptLLMCallCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext"
            )
        )
    )
    public fun interceptAfterLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        interceptLLMCallCompleted(feature, handle)
    }

    /**
     * Intercepts and handles tool calls for the specified feature and its implementation.
     * Updates the tool call handler for the given feature key with a custom handler.
     */
    @Deprecated(
        message = "Please use interceptToolCallStarting instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolCallStarting(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext"
            )
        )
    )
    public fun interceptToolCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        interceptToolCallStarting(feature, handle)
    }

    /**
     * Intercepts the result of a tool call with a custom handler for a specific feature.
     */
    @Deprecated(
        message = "Please use interceptToolCallCompleted instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolCallCompleted(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext"
            )
        )
    )
    public fun interceptToolCallResult(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        interceptToolCallCompleted(feature, handle)
    }

    /**
     * Sets up an interception mechanism to handle tool call failures for a specific feature.
     */
    @Deprecated(
        message = "Please use interceptToolCallFailed instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolCallFailed(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext"
            )
        )
    )
    public fun interceptToolCallFailure(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        interceptToolCallFailed(feature, handle)
    }

    /**
     * Intercepts validation errors encountered during the execution of tools associated with the specified feature.
     */
    @Deprecated(
        message = "Please use interceptToolValidationFailed instead. This method is deprecated and will be removed in the next release.",
        replaceWith = ReplaceWith(
            expression = "interceptToolValidationFailed(feature, handle)",
            imports = arrayOf(
                "ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext"
            )
        )
    )
    public fun interceptToolValidationError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        interceptToolValidationFailed(feature, handle)
    }

    //endregion Deprecated Interceptors

    //region Private Methods

    private fun installFeaturesFromSystemConfig() {
        val featuresFromSystemConfig = readFeatureKeysFromSystemVariables()
        val filteredSystemFeaturesToInstall = filterSystemFeaturesToInstall(featuresFromSystemConfig)

        filteredSystemFeaturesToInstall.forEach { systemFeatureKey ->
            installSystemFeature(systemFeatureKey)
        }
    }

    /**
     * Read feature keys from system variables.
     *
     * @return List of feature keys as a string.
     *         For example, ["debugger", "tracing"]
     */
    private fun readFeatureKeysFromSystemVariables(): List<String> {
        val collectedFeaturesKeys = mutableListOf<String>()

        @OptIn(ExperimentalAgentsApi::class)
        getEnvironmentVariableOrNull(FeatureSystemVariables.KOOG_FEATURES_ENV_VAR_NAME)
            ?.let { featuresString ->
                featuresString.split(",").forEach { featureString ->
                    collectedFeaturesKeys.add(featureString.trim())
                }
            }

        @OptIn(ExperimentalAgentsApi::class)
        getVMOptionOrNull(FeatureSystemVariables.KOOG_FEATURES_VM_OPTION_NAME)
            ?.let { featuresString ->
                featuresString.split(",").forEach { featureString ->
                    collectedFeaturesKeys.add(featureString.trim())
                }
            }

        return collectedFeaturesKeys.toList()
    }

    /**
     * Filter system features to install based on the provided feature keys.
     *
     * @return List of [AIAgentStorageKey]s with filtered system features to install.
     *         For example, [AIAgentStorageKey("debugger")]
     */
    private fun filterSystemFeaturesToInstall(featureKeys: List<String>): List<AIAgentStorageKey<*>> {
        val filteredSystemFeaturesToInstall = mutableListOf<AIAgentStorageKey<*>>()

        // Check config features exist in the system features list
        featureKeys.forEach { configFeatureKey ->
            val systemFeatureKey = systemFeatures.find { systemFeature -> systemFeature.name == configFeatureKey }

            // Check requested feature is in the known system features list
            if (systemFeatureKey == null) {
                logger.warn {
                    "Feature with key '$configFeatureKey' does not exist in the known system features list:\n" +
                        systemFeatures.joinToString("\n") { " - ${it.name}" }
                }
                return@forEach
            }

            // Ignore system features if already installed by a user
            if (registeredFeatures.keys.any { registerFeatureKey -> registerFeatureKey.name == configFeatureKey }) {
                logger.debug {
                    "Feature with key '$configFeatureKey' has already been registered. " +
                        "Skipping system feature from config registration."
                }
                return@forEach
            }

            filteredSystemFeaturesToInstall.add(systemFeatureKey)
        }

        return filteredSystemFeaturesToInstall.toList()
    }

    @OptIn(ExperimentalAgentsApi::class)
    private fun installSystemFeature(featureKey: AIAgentStorageKey<*>) {
        logger.debug { "Installing system feature: ${featureKey.name}" }
        when (featureKey) {
            Debugger.key -> {
                when (this) {
                    is AIAgentGraphPipeline -> {
                        this.install(Debugger) {
                            // Use default configuration
                        }
                    }
                    is AIAgentFunctionalPipeline -> {
                        this.install(Debugger) {
                            // Use default configuration
                        }
                    }
                }
            }
            else -> {
                error(
                    "Unsupported system feature key: ${featureKey.name}. " +
                        "Please make sure all system features are registered in the systemFeatures list.\n" +
                        "Current system features list:\n${systemFeatures.joinToString("\n") { " - ${it.name}" }}"
                )
            }
        }
    }

    protected fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = handler@{ eventContext ->
        val featureConfig = registeredFeatures[feature.key]?.featureConfig

        if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
            return@handler
        }

        handle(eventContext)
    }

    protected fun createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend AgentEnvironmentTransformingContext.(AIAgentEnvironment) -> AIAgentEnvironment
    ): suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment =
        handler@{ eventContext, env ->
            val featureConfig = registeredFeatures[feature.key]?.featureConfig

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
