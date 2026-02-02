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
import ai.koog.agents.core.feature.handler.AgentLifecycleContextEventHandler
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.core.feature.handler.AgentLifecycleEventType
import ai.koog.agents.core.feature.handler.AgentLifecycleHandlersCollector
import ai.koog.agents.core.feature.handler.AgentLifecycleTransformEventHandler
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentEnvironmentTransformingContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
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

    // TODO: SD -- add comment
    private val agentLifecycleHandlersCollector = AgentLifecycleHandlersCollector()

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
    internal suspend fun prepareFeatures() {
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
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param runId The unique identifier for the agent run
     * @param agent The agent instance for which the execution has started
     * @param context The context of the agent execution, providing access to the agent environment and context features
     */
    // TODO: SD -- rename all to invokeOnAgentStarting
    @OptIn(InternalAgentsApi::class)
    public suspend fun <TInput, TOutput> onAgentStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentStarting,
            context = AgentStartingContext(eventId, executionInfo, agent, runId, context)
        )
    }

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     * @param context The context of the agent execution, providing access to the agent environment and context features
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onAgentCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        result: Any?,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentCompleted,
            context = AgentCompletedContext(eventId, executionInfo, agentId, runId, result, context)
        )
    }

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The [Throwable] exception instance that was thrown during agent execution
     * @param context The context of the agent execution, providing access to the agent environment and context features
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onAgentExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        throwable: Throwable,
        context: AIAgentContext,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentExecutionFailed,
            context = AgentExecutionFailedContext(eventId, executionInfo, agentId, runId, throwable, context)
        )
    }

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the agent environment transformation event;
     * @param agentId The unique identifier of the agent that will be closed;
     * @param context The context of the agent execution, providing access to the agent environment and context features;
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onAgentClosing(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentClosing,
            context = AgentClosingContext(eventId, executionInfo, agentId)
        )
    }

    /**
     * Transforms the agent environment by applying all registered environment transformers.
     *
     * This method allows features to modify or enhance the agent's environment before it starts execution.
     * Each registered handler can apply its own transformations to the environment in sequence.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the agent environment transformation event;
     * @param agent The agent instance for which the environment is being transformed;
     * @param baseEnvironment The initial environment to be transformed;
     *
     * @return The transformed environment after all handlers have been applied.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onAgentEnvironmentTransforming(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        @OptIn(InternalAgentsApi::class)
        return invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentEnvironmentTransforming,
            context = AgentEnvironmentTransformingContext(eventId, executionInfo, agent),
            entity = baseEnvironment
        )
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the strategy event;
     * @param strategy The strategy that has started execution;
     * @param context The context of the strategy execution.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.StrategyStarting,
            context = StrategyStartingContext(eventId, executionInfo, strategy, context)
        )
    }

    /**
     * Notifies all registered strategy handlers that a strategy has finished execution.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the strategy event
     * @param strategy The strategy that has finished execution
     * @param context The context of the strategy execution
     * @param result The result produced by the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onStrategyCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.StrategyCompleted,
            context = StrategyCompletedContext(eventId, executionInfo, strategy, context, result, resultType)
        )
    }

    //endregion Invoke Strategy Handlers

    //region Invoke LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the LLM call event;
     * @param runId The unique identifier for the current run;
     * @param prompt The prompt that will be sent to the language model;
     * @param model The language model instance that will process the request;
     * @param tools The list of tool descriptors available for the LLM call;
     * @param context The AI agent context in which the LLM call is being executed.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onLLMCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMCallStarting,
            context = LLMCallStartingContext(eventId, executionInfo, runId, prompt, model, tools, context)
        )
    }

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the LLM call event;
     * @param runId Identifier for the current run;
     * @param prompt The prompt that was sent to the language model;
     * @param model The language model instance that processed the request;
     * @param tools The list of tool descriptors that were available for the LLM call;
     * @param responses The response messages received from the language model;
     * @param moderationResponse The moderation response, if any, received from the language model;
     * @param context The AI agent context in which the LLM call was executed.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onLLMCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult? = null,
        context: AIAgentContext,
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMCallCompleted,
            context = LLMCallCompletedContext(eventId, executionInfo, runId, prompt, model, tools, responses, moderationResponse, context)
        )
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    /**
     * Notifies all registered tool handlers when a tool is called.
     *
     * @param eventId The unique identifier for the current event.
     * @param executionInfo The execution information for the tool call event
     * @param runId The unique identifier for the current run.
     * @param toolCallId The unique identifier for the current tool call.
     * @param toolName The tool name that is being called
     * @param toolDescription The description of the tool that is being called.
     * @param toolArgs The arguments provided to the tool
     * @param context The AI agent context in which the tool call is being executed.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onToolCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ToolCallStarting,
            context = ToolCallStartingContext(
                eventId,
                executionInfo,
                runId,
                toolCallId,
                toolName,
                toolDescription,
                toolArgs,
                context
            )
        )
    }

    /**
     * Notifies all registered tool handlers when a validation error occurs during a tool call.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the tool call event;
     * @param runId The unique identifier for the current run;
     * @param toolCallId The unique identifier for the current tool call;
     * @param toolName The name of the tool for which validation failed;
     * @param toolDescription The description of the tool that was called;
     * @param toolArgs The arguments that failed validation;
     * @param message The validation error message;
     * @param error The [AIAgentError] validation error;
     * @param context The AI agent context associated with the tool call.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onToolValidationFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        message: String,
        error: AIAgentError,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ToolValidationFailed,
            context = ToolValidationFailedContext(
                eventId,
                executionInfo,
                runId,
                toolCallId,
                toolName,
                toolDescription,
                toolArgs,
                message,
                error,
                context
            )
        )
    }

    /**
     * Notifies all registered tool handlers when a tool call fails with an exception.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the tool call agent event
     * @param runId The unique identifier for the current run;
     * @param toolCallId The unique identifier for the current tool call;
     * @param toolName The tool name that was called;
     * @param toolDescription The description of the tool that was called;
     * @param toolArgs The arguments provided to the tool;
     * @param message A message describing the failure;
     * @param error The [AIAgentError] that caused the failure;
     * @param context The AI agent context associated with the tool call.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onToolCallFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        message: String,
        error: AIAgentError?,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ToolCallFailed,
            context = ToolCallFailedContext(
                eventId,
                executionInfo,
                runId,
                toolCallId,
                toolName,
                toolDescription,
                toolArgs,
                message,
                error,
                context
            )
        )
    }

    /**
     * Notifies all registered tool handlers about the result of a tool call.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the tool call agent event
     * @param runId The unique identifier for the current run;
     * @param toolCallId The unique identifier for the current tool call;
     * @param toolName The tool name that was called;
     * @param toolDescription The description of the tool that was called;
     * @param toolArgs The arguments that were provided to the tool;
     * @param toolResult The result produced by the tool, or null if no result was produced;
     * @param context The AI agent context associated with the tool call.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onToolCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        toolResult: JsonElement?,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.ToolCallCompleted,
            context = ToolCallCompletedContext(
                eventId,
                executionInfo,
                runId,
                toolCallId,
                toolName,
                toolDescription,
                toolArgs,
                toolResult,
                context
            )
        )
    }

    //endregion Invoke Tool Call Handlers

    //region Invoke LLM Streaming

    /**
     * Invoked before streaming from a language model begins.
     *
     * This method notifies all registered stream handlers that streaming is about to start,
     * allowing them to perform preprocessing or logging operations.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the LLM streaming event;
     * @param runId The unique identifier for this streaming session;
     * @param prompt The prompt being sent to the language model;
     * @param model The language model being used for streaming;
     * @param tools The list of available tool descriptors for this streaming session;
     * @param context The AI agent context associated with the streaming operation.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onLLMStreamingStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMStreamingStarting,
            context = LLMStreamingStartingContext(eventId, executionInfo, runId, prompt, model, tools, context)
        )
    }

    /**
     * Invoked when a stream frame is received during the streaming process.
     *
     * This method notifies all registered stream handlers about each incoming stream frame,
     * allowing them to process, transform, or aggregate the streaming content in real-time.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the LLM streaming event;
     * @param runId The unique identifier for this streaming session;
     * @param prompt The prompt being sent to the language model;
     * @param model The language model being used for streaming;
     * @param streamFrame The individual stream frame containing partial response data;
     * @param context The AI agent context associated with the streaming operation.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onLLMStreamingFrameReceived(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        streamFrame: StreamFrame,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMStreamingFrameReceived,
            context = LLMStreamingFrameReceivedContext(eventId, executionInfo, runId, prompt, model, streamFrame, context)
        )
    }

    /**
     * Invoked if an error occurs during the streaming process.
     *
     * This method notifies all registered stream handlers about the streaming error,
     * allowing them to handle or log the error.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the LLM streaming event;
     * @param runId The unique identifier for this streaming session;
     * @param prompt The prompt being sent to the language model;
     * @param model The language model being used for streaming;
     * @param throwable The exception that occurred during streaming if applicable;
     * @param context The AI agent context associated with the streaming operation.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onLLMStreamingFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        throwable: Throwable,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMStreamingFailed,
            context = LLMStreamingFailedContext(eventId, executionInfo, runId, prompt, model, throwable, context)
        )
    }

    /**
     * Invoked after streaming from a language model completes.
     *
     * This method notifies all registered stream handlers that streaming has finished,
     * allowing them to perform post-processing, cleanup, or final logging operations.
     *
     * @param eventId The unique identifier for the event group;
     * @param executionInfo The execution information for the LLM streaming event;
     * @param runId The unique identifier for this streaming session;
     * @param prompt The prompt that was sent to the language model;
     * @param model The language model that was used for streaming;
     * @param tools The list of tool descriptors that were available for this streaming session;
     * @param context The AI agent context associated with the streaming operation.
     */
    @OptIn(InternalAgentsApi::class)
    public suspend fun onLLMStreamingCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMStreamingCompleted,
            context = LLMStreamingCompletedContext(eventId, executionInfo, runId, prompt, model, tools, context)
        )
    }

    //endregion Invoke LLM Streaming

    //region Interceptors

    /**
     * Intercepts environment creation to allow features to modify or enhance the agent environment.
     *
     * This method registers a transformer function that will be called when an agent environment
     * is being created, allowing the feature to customize the environment based on the agent context.
     *
     * @param handle A function that transforms the environment, with access to the agent creation context
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentEnvironmentTransforming,
            handler = createConditionalHandler(feature, handle)
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
    // TODO: SD -- rename all to
    //  onAgentStarting(...)
    @OptIn(InternalAgentsApi::class)
    public fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentStarting,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentCompleted,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentExecutionFailed,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentClosing,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.StrategyStarting,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.StrategyCompleted,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMCallStarting,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMCallCompleted,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingStarting,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingFrameReceived,
            handler = createConditionalHandler(feature, handle)
        )
    }

    /**
     * Intercepts errors during the streaming process.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes stream errors
     */
    @OptIn(InternalAgentsApi::class)
    public fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingFailed,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingCompleted,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolCallStarting,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolValidationFailed,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolCallFailed,
            handler = createConditionalHandler(feature, handle)
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
    @OptIn(InternalAgentsApi::class)
    public fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolCallCompleted,
            handler = createConditionalHandler(feature, handle)
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

    /**
     * Invokes and executes all registered handlers for a given agent lifecycle event type
     * and context. The handlers are retrieved based on the specified event type and
     * executed in sequence for the provided context.
     *
     * @param eventType The type of agent lifecycle event for which the handlers should be invoked.
     * @param context The context associated with the agent lifecycle event.
     */
    internal suspend fun <TContext : AgentLifecycleEventContext> invokeRegisteredHandlersForEvent(
        eventType: AgentLifecycleEventType,
        context: TContext
    ) {
        val registeredHandlers = agentLifecycleHandlersCollector.getHandlersForEvent<TContext, Unit>(eventType)

        registeredHandlers.forEach { (featureKey, handlers) ->
            logger.trace { "Execute registered handlers (feature: ${featureKey.name}, event: ${context.eventType})" }
            handlers.forEach { handler ->
                if (handler !is AgentLifecycleContextEventHandler) {
                    logger.warn {
                        "Expected to process instance of <${AgentLifecycleContextEventHandler::class.simpleName}>, " +
                            "but got <${handler::class.simpleName}>. Skip it."
                    }
                    return@forEach
                }

                handler.handle(context)
            }
        }
    }

    /**
     * Invokes all registered handlers for a given event type, allowing them to process and possibly
     * transform the provided entity. Handlers are executed in the order they are registered.
     *
     * Note: Each handler is run against the last entity state. The handler receives a modified entity from a previous handler
     *       and will execute against this updated entity.
     *
     * @param eventType The type of event for which handlers need to be invoked.
     * @param context The context of the event, including related state and metadata.
     * @param entity The entity that will be processed and potentially transformed by the handlers.
     * @return The transformed entity after all applicable handlers have been invoked.
     */
    internal suspend fun <TContext : AgentLifecycleEventContext, TResult : Any> invokeRegisteredHandlersForEvent(
        eventType: AgentLifecycleEventType,
        context: TContext,
        entity: TResult
    ): TResult {
        val registeredHandlers = agentLifecycleHandlersCollector.getHandlersForEvent<TContext, TResult>(eventType)

        var currentEntity = entity

        registeredHandlers.forEach { (featureKey, handlers) ->
            logger.trace { "Execute registered handlers (feature: ${featureKey.name}, event: ${context.eventType})" }
            handlers.forEach { handler ->
                if (handler !is AgentLifecycleTransformEventHandler) {
                    logger.warn {
                        "Expected to process instance of <${AgentLifecycleTransformEventHandler::class.simpleName}>, " +
                            "but got <${handler::class.simpleName}>. Skip it."
                    }
                    return@forEach
                }
                val updatedEntity = handler.handle(context, currentEntity)
                currentEntity = updatedEntity
            }
        }

        return currentEntity
    }

    /**
     * Registers a handler for a specific feature and event type within the agent's lifecycle.
     *
     * @param TContext the type of the context associated with the agent lifecycle event.
     * @param featureKey the key representing the feature for which the handler is being added.
     * @param eventType the type of agent lifecycle event to associate with the handler.
     * @param handler the handler to invoke when the specified event occurs for the given feature.
     */
    internal fun <TContext : AgentLifecycleEventContext> addHandlerForFeature(
        featureKey: AIAgentStorageKey<*>,
        eventType: AgentLifecycleEventType,
        handler: AgentLifecycleContextEventHandler<TContext>
    ) {
        agentLifecycleHandlersCollector.addHandlerForFeature(
            featureKey = featureKey,
            eventType = eventType,
            handler = handler
        )
    }

    /**
     * Adds a handler for a specific feature associated with an agent lifecycle event type.
     *
     * @param TContext The type of the context for the agent lifecycle event.
     * @param TReturn The return type of the handler.
     * @param featureKey The storage key representing the feature for which the handler is being added.
     * @param eventType The type of the agent lifecycle event that this handler will respond to.
     * @param handler The handler function to process the specified event.
     */
    internal fun <TContext : AgentLifecycleEventContext, TReturn : Any> addHandlerForFeature(
        featureKey: AIAgentStorageKey<*>,
        eventType: AgentLifecycleEventType,
        handler: AgentLifecycleTransformEventHandler<TContext, TReturn>
    ) {
        agentLifecycleHandlersCollector.addHandlerForFeature(
            featureKey = featureKey,
            eventType = eventType,
            handler = handler
        )
    }

    /**
     * Creates a conditional handler that executes the provided handling logic only if the condition
     * based on the feature's configuration is satisfied.
     *
     * @param TContext The type of the context for the agent lifecycle event.
     * @param feature The AI agent feature whose configuration is checked to determine whether the handler should execute.
     * @param handle A suspending function that defines the handling logic to be executed when conditions are met.
     * @return A function that evaluates the condition and executes the handling logic if permitted.
     */
    internal fun <TContext : AgentLifecycleEventContext> createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend (TContext) -> Unit
    ): suspend (TContext) -> Unit = handler@{ eventContext ->
        val featureConfig = registeredFeatures[feature.key]?.featureConfig

        if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
            return@handler
        }

        handle(eventContext)
    }

    /**
     * Creates a conditional handler that processes an entity based on the configuration of the specified feature.
     * The handler only processes the entity if the feature configuration accepts the given context.
     *
     * @param feature The AI agent feature used to determine the condition for handling.
     * @param handle A suspend function that defines how the entity should be processed if the condition is met.
     * @return A function that takes the event context and entity as parameters and returns the processed or original entity.
     */
    internal fun <TContext : AgentLifecycleEventContext, TResult : Any> createConditionalHandler(
        feature: AIAgentFeature<*, *>,
        handle: suspend (TContext, TResult) -> TResult
    ): suspend (TContext, TResult) -> TResult =
        handler@{ eventContext, entity ->
            val featureConfig = registeredFeatures[feature.key]?.featureConfig

            if (featureConfig != null && !featureConfig.isAccepted(eventContext)) {
                return@handler entity
            }

            handle(eventContext, entity)
        }

    /**
     * Determines whether the given event context is accepted based on the feature configuration's event filter.
     *
     * @param eventContext The context of the agent lifecycle event to be evaluated.
     * @return `true` if the event context is accepted by the event filter; otherwise, `false`.
     */
    private fun FeatureConfig.isAccepted(eventContext: AgentLifecycleEventContext): Boolean {
        return this.eventFilter.invoke(eventContext)
    }

    //endregion Private Methods
}
