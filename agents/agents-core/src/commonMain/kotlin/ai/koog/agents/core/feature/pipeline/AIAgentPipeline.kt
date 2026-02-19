@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
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
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KType

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
public expect abstract class AIAgentPipeline(agentConfig: AIAgentConfig, clock: Clock) : AIAgentPipelineAPI {
    /**
     * Provides access to a `Clock` instance representing the current system time.
     * The `Clock` can be used to retrieve the current time, create date-time instances,
     * or perform operations based on a specific moment in time.
     */
    public override val clock: Clock

    /**
     * Represents the configuration settings for the AI agent.
     *
     * This property contains parameters and options that define the behavior
     * and capabilities of the agent. It can be overridden to customize the
     * configuration as needed in subclasses.
     */
    public override val config: AIAgentConfig

    /**
     * Retrieves a feature implementation from the current pipeline using the specified [feature], if it is registered.
     *
     * @param TFeature A feature implementation type.
     * @param feature A feature to fetch.
     * @param featureClass The [KClass] of the feature to be retrieved.
     * @return The feature associated with the provided key, or null if no matching feature is found.
     * @throws IllegalArgumentException if the specified [featureClass] does not correspond to a registered feature.
     */
    public override fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature?

    /**
     * Installs a feature into the AI agent storage using the provided feature key, configuration, and implementation.
     *
     * @param featureKey The unique key identifying the feature to be installed.
     * @param featureConfig The configuration details required to initialize the feature.
     * @param featureImpl The implementation instance of the feature to be installed.
     */
    public override fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: AIAgentStorageKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl,
    )

    /**
     * Uninstalls the specified feature associated with the given key from storage.
     *
     * @param featureKey The key identifying the feature to be uninstalled.
     */
    public override suspend fun uninstall(
        featureKey: AIAgentStorageKey<*>
    )

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
    // TODO: SD -- make this methods internal
    @OptIn(InternalAgentsApi::class)
    public override suspend fun <TInput, TOutput> onAgentStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        agent: AIAgent<*, *>,
        context: AIAgentContext
    )

    /**
     * Notifies all registered handlers that an agent has finished execution.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that finished execution
     * @param runId The unique identifier of the agent run
     * @param result The result produced by the agent, or null if no result was produced
     */
    public override suspend fun onAgentCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        result: Any?,
        context: AIAgentContext
    )

    /**
     * Notifies all registered handlers about an error that occurred during agent execution.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that encountered the error
     * @param runId The unique identifier of the agent run
     * @param throwable The [Throwable] exception instance that was thrown during agent execution
     */
    public override suspend fun onAgentExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        throwable: Throwable,
        context: AIAgentContext
    )

    /**
     * Invoked before an agent is closed to perform necessary pre-closure operations.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agentId The unique identifier of the agent that will be closed.
     */
    public override suspend fun onAgentClosing(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
    )

    /**
     * Transforms the agent environment by applying all registered environment transformers.
     *
     * This method allows features to modify or enhance the agent's environment before it starts execution.
     * Each registered handler can apply its own transformations to the environment in sequence.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the agent environment transformation event
     * @param agent The agent instance for which the environment is being transformed
     * @param baseEnvironment The initial environment to be transformed
     * @return The transformed environment after all handlers have been applied
     */
    public override suspend fun onAgentEnvironmentTransforming(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment,
    ): AIAgentEnvironment

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    /**
     * Notifies all registered strategy handlers that a strategy has started execution.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the strategy event
     * @param strategy The strategy that has started execution
     * @param context The context of the strategy execution
     */
    @OptIn(InternalAgentsApi::class)
    public override suspend fun onStrategyStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext
    )

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
    public override suspend fun onStrategyCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        strategy: AIAgentStrategy<*, *, *>,
        context: AIAgentContext,
        result: Any?,
        resultType: KType,
    )

    //endregion Trigger Strategy Handlers

    //region Trigger LLM Call Handlers

    /**
     * Notifies all registered LLM handlers before a language model call is made.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the LLM call event
     * @param runId The unique identifier for the current run.
     * @param prompt The prompt that will be sent to the language model
     * @param model The language model instance that will process the request
     * @param tools The list of tool descriptors available for the LLM call
     */
    public override suspend fun onLLMCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    )

    /**
     * Notifies all registered LLM handlers after a language model call has completed.
     *
     * @param eventId The unique identifier for the event group.
     * @param executionInfo The execution information for the LLM call event
     * @param runId Identifier for the current run.
     * @param prompt The prompt that was sent to the language model
     * @param model The language model instance that processed the request
     * @param tools The list of tool descriptors that were available for the LLM call
     * @param responses The response messages received from the language model
     * @param moderationResponse The moderation response, if any, received from the language model
     */
    public override suspend fun onLLMCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        responses: List<Message.Response>,
        moderationResponse: ModerationResult?,
        context: AIAgentContext
    )

    //endregion Trigger LLM Call Handlers

    //region Trigger Tool Call Handlers

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
     */
    public override suspend fun onToolCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        context: AIAgentContext
    )

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
     * @param error The [AIAgentError] validation error.
     */
    public override suspend fun onToolValidationFailed(
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
    )

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
     * @param message A message describing the failure.
     * @param error The [AIAgentError] that caused the failure.
     */
    public override suspend fun onToolCallFailed(
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
    )

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
     * @param toolResult The result produced by the tool, or null if no result was produced.
     */
    public override suspend fun onToolCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JsonObject,
        toolResult: JsonElement?,
        context: AIAgentContext
    )

    //endregion Trigger Tool Call Handlers

    //region Trigger LLM Streaming

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
     * @param tools The list of available tool descriptors for this streaming session.
     */
    public override suspend fun onLLMStreamingStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    )

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
     * @param streamFrame The individual stream frame containing partial response data.
     */
    public override suspend fun onLLMStreamingFrameReceived(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        streamFrame: StreamFrame,
        context: AIAgentContext
    )

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
     * @param throwable The exception that occurred during streaming if applicable.
     */
    public override suspend fun onLLMStreamingFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        throwable: Throwable,
        context: AIAgentContext
    )

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
     * @param tools The list of tool descriptors that were available for this streaming session.
     */
    public override suspend fun onLLMStreamingCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        context: AIAgentContext
    )

    //endregion Trigger LLM Streaming

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
    public override fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment
    )

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
    public override fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    )

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
    public override fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    )

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
    public override fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    )

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
    public override fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    )

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
    public override fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    )

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
    public override fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    )

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
    public override fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    )

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
    public override fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    )

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
    public override fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    )

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
    public override fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    )

    /**
     * Intercepts errors during the streaming process.
     *
     * @param feature The feature associated with this handler.
     * @param handle The handler that processes stream errors
     */
    public override fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    )

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
    public override fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    )

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
    public override fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    )

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
    public override fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    )

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
    public override fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    )

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
    public override fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    )

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
    public override fun interceptBeforeAgentStarted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    )

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
    public override fun interceptAgentFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    )

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
    public override fun interceptAgentRunError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentExecutionFailedContext) -> Unit
    )

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
    public override fun interceptAgentBeforeClose(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentClosingContext) -> Unit
    )

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
    public override fun interceptStrategyStart(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    )

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
    public override fun interceptStrategyFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    )

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
    public override fun interceptBeforeLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    )

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
    public override fun interceptAfterLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    )

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
    public override fun interceptToolCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    )

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
    public override fun interceptToolCallResult(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    )

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
    public override fun interceptToolCallFailure(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    )

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
    public override fun interceptToolValidationError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    )

    //endregion Deprecated Interceptors

    //region public and Internal Methods

    @InternalAgentsApi
    public override suspend fun prepareFeatures()

    @InternalAgentsApi
    public override suspend fun closeAllFeaturesMessageProcessors()

    //endregion public and Internal Methods
}
