package ai.grazie.code.agents.local.features.tracing.feature

import ai.grazie.code.agents.local.agent.LocalAgentStorageKey
import ai.grazie.code.agents.local.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.local.features.AIAgentPipeline
import ai.grazie.code.agents.local.features.KotlinAIAgentFeature
import ai.grazie.code.agents.local.features.common.model.*
import ai.grazie.code.agents.local.features.message.FeatureMessageProcessorUtil.onMessageForEachSafe
import ai.grazie.code.agents.local.graph.LocalAgentNode
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import ai.jetbrains.code.prompt.message.Message

/**
 * Feature that collects tracing data during agent execution and sends it to a feature message processor.
 */
class TraceFeature {

    companion object Feature : KotlinAIAgentFeature<TraceFeatureConfig, TraceFeature> {

        private val logger: MPPLogger =
            LoggerFactory.create("ai.grazie.code.agents.local.features.tracing.feature.TracingFeature")

        override val key: LocalAgentStorageKey<TraceFeature> =
            LocalAgentStorageKey("code-agents-local-features-tracing")

        override fun createInitialConfig() = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${TraceFeature::class.simpleName}" }

            if (config.messageProcessor.isEmpty()) {
                logger.warning { "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target." }
            }

            val feature = TraceFeature()

            pipeline.interceptAgentCreated(this, feature) intercept@{
                val event = AgentCreateEvent(
                    strategyName = strategy.name,
                )
                if (!config.messageFilter(event)) { return@intercept }
                readStages { _ -> config.messageProcessor.onMessageForEachSafe(event) }
            }

            pipeline.interceptStrategyStarted(this, feature) intercept@{
                val event = StrategyStartEvent(
                    strategyName = strategy.name,
                )
                if (!config.messageFilter(event)) { return@intercept }
                readStages { _ -> config.messageProcessor.onMessageForEachSafe(event) }
            }

            pipeline.interceptBeforeLLMCall(this, feature) intercept@{ prompt ->
                val event = LLMCallStartEvent(
                    prompt = prompt.messages.firstOrNull { it.role == Message.Role.User }?.content ?: ""
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCall(this, feature) intercept@{ response ->
                val event = LLMCallEndEvent(
                    response = response
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptBeforeLLMCallWithTools(this, feature) intercept@{ prompt, tools ->
                val event = LLMCallWithToolsStartEvent(
                    prompt = prompt.messages.firstOrNull { it.role == Message.Role.User }?.content ?: "",
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCallWithTools(this, feature) intercept@{ responses, tools ->
                val event = LLMCallWithToolsEndEvent(
                    responses = responses.map { it.content },
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptBeforeToolCall(this, feature) intercept@{ tools ->
                val event = ToolCallsStartEvent(
                    tools = tools
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterToolCall(this, feature) intercept@{ results ->
                val event = ToolCallsEndEvent(
                    results = results.map { it.toMessage() }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptBeforeNode(this, feature) intercept@{ node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any? ->
                val event = NodeExecutionStartEvent(
                    nodeName = node.name,
                    stageName = context.stageName,
                    input = input?.toString() ?: ""
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterNode(this, feature) intercept@{ node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any? ->
                val event = NodeExecutionEndEvent(
                    nodeName = node.name,
                    stageName = context.stageName,
                    input = input?.toString() ?: "",
                    output = output?.toString() ?: ""
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }
        }
    }
}
