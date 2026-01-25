package ai.koog.agents.core.feature.pipeline

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
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
 * Default implementation of [AIAgentPipelineAPI]
 */
public class AIAgentPipelineImpl(
    override val config: AIAgentConfig,
    public override val clock: Clock
) : AIAgentPipelineAPI {

    // Notes on suppressed warnings used in this class:
    // - Some members are annotated with @Suppress to satisfy explicit API requirements
    //   (e.g., explicit public visibility) or to keep implementation details concise.
    //   These suppressions are intentional and safe.

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Represents a registered feature in the system.
     *
     * This class encapsulates the implementation of a feature and its associated configuration.
     * It is used to maintain feature details after registration.
     *
     * @property featureImpl The implementation instance of the feature.
     * @property featureConfig The configuration settings associated with the feature.
     */
    @Suppress("RedundantVisibilityModifier") // have to put public here, explicitApi requires it
    private class RegisteredFeature(
        public val featureImpl: Any,
        public val featureConfig: FeatureConfig
    )

    private val registeredFeatures: MutableMap<AIAgentStorageKey<*>, RegisteredFeature> = mutableMapOf()

    @OptIn(ExperimentalAgentsApi::class)
    private val systemFeatures: Set<AIAgentStorageKey<*>> = setOf(
        Debugger.key
    )

    // TODO: SD -- add comment
    private val agentLifecycleHandlersCollector = AgentLifecycleHandlersCollector()

    public override fun <TFeature : Any> feature(
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

    public override fun <TConfig : FeatureConfig, TFeatureImpl : Any> install(
        featureKey: AIAgentStorageKey<TFeatureImpl>,
        featureConfig: TConfig,
        featureImpl: TFeatureImpl,
    ) {
        registeredFeatures[featureKey] = RegisteredFeature(featureImpl, featureConfig)
    }

    public override suspend fun uninstall(
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

    internal suspend fun prepareFeature(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { processor ->
            logger.debug { "Start preparing processor: ${processor::class.simpleName}" }
            processor.initialize()
            logger.debug { "Finished preparing processor: ${processor::class.simpleName}" }
        }
    }

    @InternalAgentsApi
    override suspend fun prepareFeatures() {
        // Install system features (if exist)
        installFeaturesFromSystemConfig()

        // Prepare features
        registeredFeatures.values.forEach { featureConfig ->
            prepareFeature(featureConfig.featureConfig)
        }
    }

    internal suspend fun closeFeatureMessageProcessors(featureConfig: FeatureConfig) {
        featureConfig.messageProcessors.forEach { provider ->
            logger.trace { "Start closing feature processor: ${featureConfig::class.simpleName}" }
            provider.close()
            logger.trace { "Finished closing feature processor: ${featureConfig::class.simpleName}" }
        }
    }

    @InternalAgentsApi
    override suspend fun closeAllFeaturesMessageProcessors() {
        registeredFeatures.values.forEach { registerFeature ->
            closeFeatureMessageProcessors(registerFeature.featureConfig)
        }
    }

    //endregion Internal Handlers

    //region Invoke Agent Handlers

    // TODO: SD -- rename all to invokeOnAgentStarting
    @OptIn(InternalAgentsApi::class)
    public override suspend fun <TInput, TOutput> onAgentStarting(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onAgentCompleted(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onAgentExecutionFailed(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String,
        runId: String,
        throwable: Throwable,
        context: AIAgentContext
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentExecutionFailed,
            context = AgentExecutionFailedContext(eventId, executionInfo, agentId, runId, throwable, context)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onAgentClosing(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agentId: String
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentClosing,
            context = AgentClosingContext(eventId, executionInfo, agentId, config)
        )
    }

    public override suspend fun onAgentEnvironmentTransforming(
        eventId: String,
        executionInfo: AgentExecutionInfo,
        agent: GraphAIAgent<*, *>,
        baseEnvironment: AIAgentEnvironment
    ): AIAgentEnvironment {
        @OptIn(InternalAgentsApi::class)
        return invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.AgentEnvironmentTransforming,
            context = AgentEnvironmentTransformingContext(eventId, executionInfo, agent, config),
            entity = baseEnvironment
        )
    }

    //endregion Invoke Agent Handlers

    //region Invoke Strategy Handlers

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onStrategyStarting(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onStrategyCompleted(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onLLMCallStarting(
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

    @OptIn(InternalAgentsApi::class)
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
    ) {
        invokeRegisteredHandlersForEvent(
            eventType = AgentLifecycleEventType.LLMCallCompleted,
            context = LLMCallCompletedContext(eventId, executionInfo, runId, prompt, model, tools, responses, moderationResponse, context)
        )
    }

    //endregion Invoke LLM Call Handlers

    //region Invoke Tool Call Handlers

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onToolCallStarting(
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

    @OptIn(InternalAgentsApi::class)
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

    @OptIn(InternalAgentsApi::class)
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

    @OptIn(InternalAgentsApi::class)
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onLLMStreamingStarting(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onLLMStreamingFrameReceived(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onLLMStreamingFailed(
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

    @OptIn(InternalAgentsApi::class)
    public override suspend fun onLLMStreamingCompleted(
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

    @OptIn(InternalAgentsApi::class)
    public override fun interceptEnvironmentCreated(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentEnvironmentTransformingContext, AIAgentEnvironment) -> AIAgentEnvironment
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentEnvironmentTransforming,
            handler = createConditionalHandler(feature, handle)
        )
    }

    // TODO: SD -- rename all to
    //  onAgentStarting(...)
    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (AgentStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentExecutionFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentExecutionFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentExecutionFailed,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptAgentClosing(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: AgentClosingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.AgentClosing,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptStrategyStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.StrategyStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptStrategyCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (StrategyCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.StrategyCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMCallStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMCallCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMCallCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMCallCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingFrameReceived(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFrameReceivedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingFrameReceived,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingFailed,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptLLMStreamingCompleted(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: LLMStreamingCompletedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.LLMStreamingCompleted,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolCallStarting(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallStartingContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolCallStarting,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolValidationFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolValidationFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolValidationFailed,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolCallFailed(
        feature: AIAgentFeature<*, *>,
        handle: suspend (eventContext: ToolCallFailedContext) -> Unit
    ) {
        addHandlerForFeature(
            featureKey = feature.key,
            eventType = AgentLifecycleEventType.ToolCallFailed,
            handler = createConditionalHandler(feature, handle)
        )
    }

    @OptIn(InternalAgentsApi::class)
    public override fun interceptToolCallCompleted(
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
    ) {
        interceptAgentStarting(feature, handle)
    }

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
    ) {
        interceptAgentCompleted(feature, handle)
    }

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
    ) {
        interceptAgentExecutionFailed(feature, handle)
    }

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
    ) {
        interceptAgentClosing(feature, handle)
    }

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
    ) {
        interceptStrategyStarting(feature, handle)
    }

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
    ) {
        interceptStrategyCompleted(feature, handle)
    }

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
    ) {
        interceptLLMCallStarting(feature, handle)
    }

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
    ) {
        interceptLLMCallCompleted(feature, handle)
    }

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
    ) {
        interceptToolCallStarting(feature, handle)
    }

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
    ) {
        interceptToolCallCompleted(feature, handle)
    }

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
    ) {
        interceptToolCallFailed(feature, handle)
    }

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
