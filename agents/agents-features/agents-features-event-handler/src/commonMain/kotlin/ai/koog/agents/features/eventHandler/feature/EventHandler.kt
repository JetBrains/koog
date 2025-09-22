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
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
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
            pipeline.interceptAgentStarting(interceptContext) intercept@{ eventContext ->
                config.invokeOnBeforeAgentStarted(eventContext)
            }

            pipeline.interceptNodeExecutionStarting(interceptContext) intercept@{ eventContext: NodeExecutionStartingContext ->
                config.invokeOnBeforeNode(eventContext)
            }

            pipeline.interceptNodeExecutionCompleted(interceptContext) intercept@{ eventContext: NodeExecutionCompletedContext ->
                config.invokeOnAfterNode(eventContext)
            }

            pipeline.interceptNodeExecutionFailed(
                interceptContext
            ) intercept@{ eventContext: NodeExecutionFailedContext ->
                config.invokeOnNodeExecutionError(eventContext)
            }
        }

        private fun registerCommonPipelineHandlers(
            config: EventHandlerConfig,
            pipeline: AIAgentPipeline,
            interceptContext: InterceptContext<EventHandler>
        ) {
            pipeline.interceptAgentCompleted(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentFinished(eventContext)
            }

            pipeline.interceptAgentExecutionFailed(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentRunError(eventContext)
            }

            pipeline.interceptAgentClosing(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentBeforeClose(eventContext)
            }

            pipeline.interceptStrategyStarting(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyStarted(eventContext)
            }

            pipeline.interceptStrategyCompleted(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyFinished(eventContext)
            }

            pipeline.interceptLLMCallStarting(interceptContext) intercept@{ eventContext: LLMCallStartingContext ->
                config.invokeOnBeforeLLMCall(eventContext)
            }

            pipeline.interceptLLMCallCompleted(interceptContext) intercept@{ eventContext: LLMCallCompletedContext ->
                config.invokeOnAfterLLMCall(eventContext)
            }

            pipeline.interceptToolExecutionStarting(interceptContext) intercept@{ eventContext: ToolExecutionStartingContext ->
                config.invokeOnToolCall(eventContext)
            }

            pipeline.interceptToolValidationFailed(
                interceptContext
            ) intercept@{ eventContext: ToolValidationFailedContext ->
                config.invokeOnToolValidationError(eventContext)
            }

            pipeline.interceptToolExecutionFailed(interceptContext) intercept@{ eventContext: ToolExecutionFailedContext ->
                config.invokeOnToolCallFailure(eventContext)
            }

            pipeline.interceptToolExecutionCompleted(interceptContext) intercept@{ eventContext: ToolExecutionCompletedContext ->
                config.invokeOnToolCallResult(eventContext)
            }

            pipeline.interceptLLMStreamingStarting(interceptContext) intercept@{ eventContext: LLMStreamingStartingContext ->
                config.invokeOnBeforeStream(eventContext)
            }

            pipeline.interceptLLMStreamingFrameReceived(interceptContext) intercept@{ eventContext: LLMStreamingFrameReceivedContext ->
                config.invokeOnStreamFrame(eventContext)
            }

            pipeline.interceptLLMStreamingFailed(interceptContext) intercept@{ eventContext ->
                config.invokeOnStreamError(eventContext)
            }

            pipeline.interceptLLMStreamingCompleted(interceptContext) intercept@{ eventContext: LLMStreamingCompletedContext ->
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
