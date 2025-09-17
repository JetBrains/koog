package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.AIAgentGraphPipeline
import ai.koog.agents.core.feature.AIAgentNonGraphFeature
import ai.koog.agents.core.feature.AIAgentNonGraphPipeline
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.handler.AfterLLMCallContext
import ai.koog.agents.core.feature.handler.AfterStreamContext
import ai.koog.agents.core.feature.handler.BeforeLLMCallContext
import ai.koog.agents.core.feature.handler.BeforeStreamContext
import ai.koog.agents.core.feature.handler.NodeAfterExecuteContext
import ai.koog.agents.core.feature.handler.NodeBeforeExecuteContext
import ai.koog.agents.core.feature.handler.NodeExecutionErrorContext
import ai.koog.agents.core.feature.handler.StreamFrameContext
import ai.koog.agents.core.feature.handler.ToolCallContext
import ai.koog.agents.core.feature.handler.ToolCallFailureContext
import ai.koog.agents.core.feature.handler.ToolCallResultContext
import ai.koog.agents.core.feature.handler.ToolValidationErrorContext
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * A feature that allows hooking into various events in the agent's lifecycle.
 *
 * The EventHandler provides a way to register callbacks for different events that occur during
 * the execution of an agent, such as agent lifecycle events, strategy events, node events,
 * LLM call events, and tool call events.
 *
 * Example usage:
 * ```
 * handleEvents {
 *     onToolCall { stage, tool, toolArgs ->
 *         println("Tool called: ${tool.name} with args $toolArgs")
 *     }
 *
 *     onAgentFinished { strategyName, result ->
 *         println("Agent finished with result: $result")
 *     }
 * }
 * ```
 */
public class EventHandler {
    /**
     * Implementation of the [AIAgentFeature] interface for the [EventHandler] feature.
     *
     * This companion object provides the necessary functionality to install the [EventHandler]
     * feature into an agent's pipeline. It intercepts various events in the agent's lifecycle
     * and forwards them to the appropriate handlers defined in the [EventHandlerConfig].
     *
     * The EventHandler provides a way to register callbacks for different events that occur during
     * the execution of an agent, such as agent lifecycle events, strategy events, node events,
     * LLM call events, and tool call events.
     *
     * Example usage:
     * ```
     * handleEvents {
     *     onToolCall { stage, tool, toolArgs ->
     *         println("Tool called: ${tool.name} with args $toolArgs")
     *     }
     *
     *     onAgentFinished { strategyName, result ->
     *         println("Agent finished with result: $result")
     *     }
     * }
     */
    public companion object Feature : AIAgentGraphFeature<EventHandlerConfig, EventHandler>, AIAgentNonGraphFeature<EventHandlerConfig, EventHandler> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<EventHandler> =
            AIAgentStorageKey("agents-features-event-handler")

        override fun createInitialConfig(): EventHandlerConfig = EventHandlerConfig()

        override fun install(
            config: EventHandlerConfig,
            pipeline: AIAgentGraphPipeline,
        ) {
            logger.info { "Start installing feature: ${EventHandler::class.simpleName}" }

            val featureImpl = EventHandler()
            val interceptContext: InterceptContext<EventHandler> = InterceptContext(this, featureImpl)
            registerCommonPipelineHandlers(config, pipeline, interceptContext)
            registerGraphPipelineHandlers(config, pipeline, interceptContext)
        }

        private fun registerGraphPipelineHandlers(
            config: EventHandlerConfig,
            pipeline: AIAgentGraphPipeline,
            interceptContext: InterceptContext<EventHandler>
        ) {
            pipeline.interceptBeforeAgentStarted(interceptContext) intercept@{ eventContext ->
                config.invokeOnBeforeAgentStarted(eventContext)
            }

            pipeline.interceptBeforeNode(interceptContext) intercept@{ eventContext: NodeBeforeExecuteContext ->
                config.invokeOnBeforeNode(eventContext)
            }

            pipeline.interceptAfterNode(interceptContext) intercept@{ eventContext: NodeAfterExecuteContext ->
                config.invokeOnAfterNode(eventContext)
            }

            pipeline.interceptNodeExecutionError(
                interceptContext
            ) intercept@{ eventContext: NodeExecutionErrorContext ->
                config.invokeOnNodeExecutionError(eventContext)
            }
        }

        private fun registerCommonPipelineHandlers(
            config: EventHandlerConfig,
            pipeline: AIAgentPipeline,
            interceptContext: InterceptContext<EventHandler>
        ) {
            pipeline.interceptAgentFinished(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentFinished(eventContext)
            }

            pipeline.interceptAgentRunError(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentRunError(eventContext)
            }

            pipeline.interceptAgentBeforeClosed(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentBeforeClose(eventContext)
            }

            pipeline.interceptStrategyStarted(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyStarted(eventContext)
            }

            pipeline.interceptStrategyFinished(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyFinished(eventContext)
            }

            pipeline.interceptBeforeLLMCall(interceptContext) intercept@{ eventContext: BeforeLLMCallContext ->
                config.invokeOnBeforeLLMCall(eventContext)
            }

            pipeline.interceptAfterLLMCall(interceptContext) intercept@{ eventContext: AfterLLMCallContext ->
                config.invokeOnAfterLLMCall(eventContext)
            }

            pipeline.interceptToolCall(interceptContext) intercept@{ eventContext: ToolCallContext ->
                config.invokeOnToolCall(eventContext)
            }

            pipeline.interceptToolValidationError(
                interceptContext
            ) intercept@{ eventContext: ToolValidationErrorContext ->
                config.invokeOnToolValidationError(eventContext)
            }

            pipeline.interceptToolCallFailure(interceptContext) intercept@{ eventContext: ToolCallFailureContext ->
                config.invokeOnToolCallFailure(eventContext)
            }

            pipeline.interceptToolCallResult(interceptContext) intercept@{ eventContext: ToolCallResultContext ->
                config.invokeOnToolCallResult(eventContext)
            }

            pipeline.interceptBeforeStream(interceptContext) intercept@{ eventContext: BeforeStreamContext ->
                config.invokeOnBeforeStream(eventContext)
            }

            pipeline.interceptOnStreamFrame(interceptContext) intercept@{ eventContext: StreamFrameContext ->
                config.invokeOnStreamFrame(eventContext)
            }

            pipeline.interceptOnStreamError(interceptContext) intercept@{ eventContext ->
                config.invokeOnStreamError(eventContext)
            }

            pipeline.interceptAfterStream(interceptContext) intercept@{ eventContext: AfterStreamContext ->
                config.invokeOnAfterStream(eventContext)
            }
        }

        override fun install(
            config: EventHandlerConfig,
            pipeline: AIAgentNonGraphPipeline
        ) {
            val featureImpl = EventHandler()
            val interceptContext: InterceptContext<EventHandler> = InterceptContext(this, featureImpl)
            registerCommonPipelineHandlers(config, pipeline, interceptContext)
        }
    }
}

/**
 * Installs the EventHandler feature and configures event handlers for an agent.
 *
 * This extension function provides a convenient way to install the EventHandler feature
 * and configure various event handlers for an agent. It allows you to define custom
 * behavior for different events that occur during the agent's execution.
 *
 * @param configure A lambda with receiver that configures the EventHandlerConfig.
 *                  Use this to set up handlers for specific events.
 *
 * Example:
 * ```
 * handleEvents {
 *     // Log when tools are called
 *     onToolCall { stage, tool, toolArgs ->
 *         println("Tool called: ${tool.name}")
 *     }
 *
 *     // Handle errors
 *     onAgentRunError { strategyName, throwable ->
 *         logger.error("Agent error: ${throwable.message}")
 *     }
 * }
 * ```
 */
public fun FeatureContext.handleEvents(configure: EventHandlerConfig.() -> Unit) {
    install(EventHandler) {
        configure()
    }
}
