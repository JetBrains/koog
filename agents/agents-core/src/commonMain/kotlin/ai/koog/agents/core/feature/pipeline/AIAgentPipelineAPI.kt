package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
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
import ai.koog.agents.core.feature.handler.llm.LLMCallFailedContext
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
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONObject
import ai.koog.serialization.TypeToken
import ai.koog.utils.time.KoogClock
import kotlin.reflect.KClass

/**
 * Platform-agnostic API for agent pipelines. Implemented by both the expect/actual AIAgentPipeline
 * and the shared AIAgentPipelineImpl.
 */
public interface AIAgentPipelineAPI {
    public val clock: KoogClock

    public val config: AIAgentConfig

    public fun <TFeature : Any> feature(
        featureClass: KClass<TFeature>,
        feature: AIAgentFeature<*, TFeature>
    ): TFeature?

    public fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: AIAgentStorageKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl,
    )

    public suspend fun uninstall(featureKey: AIAgentStorageKey<*>)

    //region Trigger Agent Handlers

    @InternalAgentsApi
    public suspend fun <TInput, TOutput> onAgentStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: AIAgent<*, *>,
        context: AIAgentContext,
        runId: String,
    )

    @InternalAgentsApi
    public suspend fun onAgentCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: AIAgent<*, *>,
        context: AIAgentContext,
        runId: String,
        result: Any?,
    )

    @InternalAgentsApi
    public suspend fun onAgentExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: AIAgent<*, *>,
        context: AIAgentContext,
        runId: String,
        error: Throwable,
    )

    @InternalAgentsApi
    public suspend fun onAgentClosing(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: AIAgent<*, *>,
    )

    @InternalAgentsApi
    public suspend fun onAgentEnvironmentTransforming(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: AIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment

    //endregion Trigger Agent Handlers

    //region Trigger Strategy Handlers

    @InternalAgentsApi
    public suspend fun onStrategyStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        strategy: AIAgentStrategy<*, *, *>,
    )

    @InternalAgentsApi
    public suspend fun onStrategyCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        strategy: AIAgentStrategy<*, *, *>,
        result: Any?,
        resultType: TypeToken
    )

    //endregion Trigger Strategy Handlers

    //region Trigger LLM Handlers

    @InternalAgentsApi
    public suspend fun onLLMCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    )

    @InternalAgentsApi
    public suspend fun onLLMCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        response: Message.Assistant?,
        moderationResponse: ModerationResult? = null,
    )

    public suspend fun onLLMCallFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        error: Throwable,
    )

    //endregion Trigger LLM Handlers

    //region Trigger Tool Handlers

    @InternalAgentsApi
    public suspend fun onToolCallStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JSONObject,
    )

    @InternalAgentsApi
    public suspend fun onToolValidationFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JSONObject,
        message: String,
        error: Throwable,
    )

    @InternalAgentsApi
    public suspend fun onToolCallFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JSONObject,
        message: String,
        error: Throwable?,
    )

    @InternalAgentsApi
    public suspend fun onToolCallCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JSONObject,
        toolResult: JSONElement?,
    )

    /**
     * Collects metadata contributions from every feature that registered a handler via
     * [provideToolCallMetadata] and returns the combined map.
     *
     * Contributions are merged in feature installation order; later contributions overwrite earlier
     * ones on key collision. The caller of [ai.koog.agents.core.environment.AIAgentEnvironment.executeTool]
     * is responsible for choosing a precedence when mixing this result with caller-supplied metadata.
     */
    @InternalAgentsApi
    public suspend fun collectToolCallMetadata(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        runId: String,
        toolCallId: String?,
        toolName: String,
        toolDescription: String?,
        toolArgs: JSONObject,
        context: AIAgentContext
    ): ToolCallMetadata

    //endregion Trigger Tool Handlers

    //region Trigger Streaming Handlers

    @InternalAgentsApi
    public suspend fun onLLMStreamingStarting(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    )

    @InternalAgentsApi
    public suspend fun onLLMStreamingFrameReceived(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        streamFrame: StreamFrame,
    )

    @InternalAgentsApi
    public suspend fun onLLMStreamingFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        error: Throwable,
    )

    @InternalAgentsApi
    public suspend fun onLLMStreamingCompleted(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        context: AIAgentContext,
        runId: String,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    )

    //endregion Trigger Streaming Handlers

    //region Interceptors

    public fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentEnvironmentTransformingContext, environment: AIAgentEnvironment) -> AIAgentEnvironment
    )

    public fun interceptAgentStarting(feature: AIAgentFeature<*, *>, handle: suspend (AgentStartingContext) -> Unit)

    public fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    )

    public fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    )

    public fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    )

    public fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    )

    public fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    )

    public fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    )

    public fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    )

    public fun interceptLLMCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallFailedContext) -> Unit
    )

    public fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    )

    public fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    )

    public fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    )

    public fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    )

    public fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    )

    /**
     * Registers a handler that contributes metadata for every tool call.
     *
     * The handler fires before the tool executes, receives the same [ToolCallStartingContext] used by
     * [interceptToolCallStarting], and returns a `Map<String, Any?>` of values to merge into the
     * metadata threaded into [ai.koog.agents.core.tools.Tool.execute].
     *
     * Use this to attach cross-cutting per-call context (trace span id, correlation id, feature flags)
     * without expanding the tool's argument schema. Return an empty map to contribute nothing.
     *
     * Merge precedence (documented in [ai.koog.agents.core.environment.ContextualAgentEnvironment]):
     * caller-supplied metadata wins over feature contributions on key collision.
     */
    public fun provideToolCallMetadata(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Map<String, Any?>
    )

    public fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    )

    public fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    )

    public fun interceptToolCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    )

    // Short aliases

    public fun interceptBeforeAgentStarted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    )

    public fun interceptAgentFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    )

    public fun interceptAgentRunError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentExecutionFailedContext) -> Unit
    )

    public fun interceptAgentBeforeClose(feature: AIAgentFeature<*, *>, handle: suspend (AgentClosingContext) -> Unit)

    public fun interceptStrategyStart(feature: AIAgentFeature<*, *>, handle: suspend (StrategyStartingContext) -> Unit)

    public fun interceptStrategyFinished(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    )

    public fun interceptBeforeLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    )

    public fun interceptAfterLLMCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    )

    public fun interceptToolCall(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    )

    public fun interceptToolCallResult(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallCompletedContext) -> Unit
    )

    public fun interceptToolCallFailure(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    )

    public fun interceptToolValidationError(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    )

    //endregion Interceptors

    @InternalAgentsApi
    public suspend fun prepareFeatures()

    @InternalAgentsApi
    public suspend fun closeAllFeaturesMessageProcessors()

    // Note: conditional handler builders and internal helper APIs are intentionally not part of the public API
}
