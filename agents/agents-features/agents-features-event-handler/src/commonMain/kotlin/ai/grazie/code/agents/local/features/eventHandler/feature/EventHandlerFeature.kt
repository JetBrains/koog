package ai.grazie.code.agents.local.features.eventHandler.feature

import ai.grazie.code.agents.core.agent.entity.LocalAgentNode
import ai.grazie.code.agents.core.agent.entity.LocalAgentStorageKey
import ai.grazie.code.agents.core.agent.entity.stage.LocalAgentStageContext
import ai.grazie.code.agents.core.feature.AIAgentPipeline
import ai.grazie.code.agents.core.feature.KotlinAIAgentFeature
import ai.grazie.utils.mpp.LoggerFactory
import ai.grazie.utils.mpp.MPPLogger

class EventHandlerFeature {

    companion object Feature : KotlinAIAgentFeature<EventHandlerFeatureConfig, EventHandlerFeature> {

        private val logger: MPPLogger =
            LoggerFactory.create("ai.grazie.code.agents.local.features.tracing.feature.EventHandlerFeature")

        override val key: LocalAgentStorageKey<EventHandlerFeature> =
            LocalAgentStorageKey("agents-features-event-handler")

        override fun createInitialConfig() = EventHandlerFeatureConfig()

        override fun install(
            config: EventHandlerFeatureConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${EventHandlerFeature::class.simpleName}" }

            val featureImpl = EventHandlerFeature()


            //region Intercept Agent Events

            pipeline.interceptAgentCreated(this, featureImpl) intercept@{
                config.onAgentCreated(strategy, agent)
            }

            pipeline.interceptAgentStarted(this, featureImpl) intercept@{ strategyName ->
                config.onAgentStarted(strategyName)
            }

            pipeline.interceptAgentFinished(this, featureImpl) intercept@{ strategyName, result ->
                config.onAgentFinished(strategyName, result)
            }

            pipeline.interceptAgentRunError(this, featureImpl) intercept@{ strategyName, throwable ->
                config.onAgentRunError(strategyName, throwable)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(this, featureImpl) intercept@{
                config.onStrategyStarted(strategy)
            }

            pipeline.interceptStrategyFinished(this, featureImpl) intercept@{ strategyName, result ->
                config.onStrategyFinished(strategyName, result)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(
                this,
                featureImpl
            ) intercept@{ node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any? ->
                config.onBeforeNode(node, context, input)
            }

            pipeline.interceptAfterNode(
                this,
                featureImpl
            ) intercept@{ node: LocalAgentNode<*, *>, context: LocalAgentStageContext, input: Any?, output: Any? ->
                config.onAfterNode(node, context, input, output)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(this, featureImpl) intercept@{ prompt ->
                config.onBeforeLLMCall(prompt)
            }

            pipeline.interceptBeforeLLMCallWithTools(this, featureImpl) intercept@{ prompt, tools ->
                config.onBeforeLLMWithToolsCall(prompt, tools)
            }

            pipeline.interceptAfterLLMCall(this, featureImpl) intercept@{ response ->
                config.onAfterLLMCall(response)
            }

            pipeline.interceptAfterLLMCallWithTools(this, featureImpl) intercept@{ response, tools ->
                config.onAfterLLMWithToolsCall(response, tools)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptBeforeToolCall(this, featureImpl) intercept@{ tools ->
                config.onBeforeToolCalls(tools)
            }

            pipeline.interceptAfterToolCall(this, featureImpl) intercept@{ results ->
                config.onAfterToolCalls(results)
            }

            pipeline.interceptToolCall(this, featureImpl) intercept@{ stage, tool, toolArgs ->
                config.onToolCall(stage, tool, toolArgs)
            }

            pipeline.interceptToolValidationError(this, featureImpl) intercept@{ stage, tool, toolArgs, value ->
                config.onToolValidationError(stage, tool, toolArgs, value)
            }

            pipeline.interceptToolCallFailure(this, featureImpl) intercept@{ stage, tool, toolArgs, throwable ->
                config.onToolCallFailure(stage, tool, toolArgs, throwable)
            }

            pipeline.interceptToolCallResult(this, featureImpl) intercept@{ stage, tool, toolArgs, result ->
                config.onToolCallResult(stage, tool, toolArgs, result)
            }

            //endregion Intercept Tool Call Events

        }
    }
}
