package ai.grazie.code.agents.local.features.tracing.feature

import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorageKey
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.feature.AgentPipeline
import ai.grazie.code.agents.core.feature.KotlinAIAgentFeature
import ai.grazie.code.agents.core.feature.model.*
import ai.grazie.code.agents.local.features.common.message.FeatureMessage
import ai.grazie.code.agents.local.features.common.message.FeatureMessageProcessorUtil.onMessageForEachSafe
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger
import ai.jetbrains.code.prompt.message.Message

/**
 * Feature that collects tracing data during agent execution and sends it to a feature message processor.
 */
class Tracing {

    companion object Feature : KotlinAIAgentFeature<TraceFeatureConfig, Tracing> {

        private val logger: MPPLogger =
            LoggerFactory.create("ai.grazie.code.agents.local.features.tracing.feature.TracingFeature")

        override val key: LocalAgentStorageKey<Tracing> =
            LocalAgentStorageKey("agents-features-tracing")

        override fun createInitialConfig() = TraceFeatureConfig()

        override fun install(
            config: TraceFeatureConfig,
            pipeline: AgentPipeline,
        ) {
            logger.info { "Start installing feature: ${Tracing::class.simpleName}" }

            if (config.messageProcessor.isEmpty()) {
                logger.warning { "Tracing Feature. No feature out stream providers are defined. Trace streaming has no target." }
            }

            val featureImpl = Tracing()

            //region Intercept Agent Events

            pipeline.interceptAgentCreated(this, featureImpl) intercept@{
                val event = AgentCreateEvent(
                    strategyName = strategy.name,
                )
                readStages { _ ->
                    processMessage(config, event)
                }
            }

            pipeline.interceptAgentStarted(this, featureImpl) intercept@{ strategyName ->
                val event = AgentStartedEvent(
                    strategyName = strategyName,
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentFinished(this, featureImpl) intercept@{ strategyName, result ->
                val event = AgentFinishedEvent(
                    strategyName = strategyName,
                    result = result,
                )
                processMessage(config, event)
            }

            pipeline.interceptAgentRunError(this, featureImpl) intercept@{ strategyName, throwable ->
                val event = AgentRunErrorEvent(
                    strategyName = strategyName,
                    error = throwable.toAgentError(),
                )
                processMessage(config, event)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(this, featureImpl) intercept@{
                val event = StrategyStartEvent(
                    strategyName = strategy.name,
                )
                readStages { _ -> processMessage(config, event) }
            }

            pipeline.interceptStrategyFinished(this, featureImpl) intercept@{ strategyName, result ->
                val event = StrategyFinishedEvent(
                    strategyName = strategyName,
                    result = result,
                )
                processMessage(config, event)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(this, featureImpl) intercept@{ node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any? ->
                val event = NodeExecutionStartEvent(
                    nodeName = node.name,
                    stageName = context.stageName,
                    input = input?.toString() ?: ""
                )
                processMessage(config, event)
            }

            pipeline.interceptAfterNode(this, featureImpl) intercept@{ node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any? ->
                val event = NodeExecutionEndEvent(
                    nodeName = node.name,
                    stageName = context.stageName,
                    input = input?.toString() ?: "",
                    output = output?.toString() ?: ""
                )
                processMessage(config, event)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(this, featureImpl) intercept@{ prompt ->
                val event = LLMCallStartEvent(
                    prompt = prompt.messages.firstOrNull { it.role == Message.Role.User }?.content ?: ""
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptBeforeLLMCallWithTools(this, featureImpl) intercept@{ prompt, tools ->
                val event = LLMCallWithToolsStartEvent(
                    prompt = prompt.messages.firstOrNull { it.role == Message.Role.User }?.content ?: "",
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCall(this, featureImpl) intercept@{ response ->
                val event = LLMCallEndEvent(
                    response = response
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            pipeline.interceptAfterLLMCallWithTools(this, featureImpl) intercept@{ responses, tools ->
                val event = LLMCallWithToolsEndEvent(
                    responses = responses.map { it.content },
                    tools = tools.map { it.name }
                )
                if (!config.messageFilter(event)) { return@intercept }
                config.messageProcessor.onMessageForEachSafe(event)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(this, featureImpl) intercept@{ stage, tool, toolArgs ->
                val event = ToolCallEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs
                )
                processMessage(config, event)
            }

            pipeline.interceptToolValidationError(this, featureImpl) intercept@{ stage, tool, toolArgs, value ->
                val event = ToolValidationErrorEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    errorMessage = value
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallFailure(this, featureImpl) intercept@{ stage, tool, toolArgs, throwable ->
                val event = ToolCallFailureEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    error = throwable.toAgentError()
                )
                processMessage(config, event)
            }

            pipeline.interceptToolCallResult(this, featureImpl) intercept@{ stage, tool, toolArgs, result ->
                val event = ToolCallResultEvent(
                    stageName = stage.name,
                    toolName = tool.name,
                    toolArgs = toolArgs,
                    result = result
                )
                processMessage(config, event)
            }

            //endregion Intercept Tool Call Events
        }

        //region Private Methods

        private suspend fun processMessage(config: TraceFeatureConfig, message: FeatureMessage) {
            if (!config.messageFilter(message)) {
                return
            }

            config.messageProcessor.onMessageForEachSafe(message)
        }

        //endregion Private Methods
    }
}
