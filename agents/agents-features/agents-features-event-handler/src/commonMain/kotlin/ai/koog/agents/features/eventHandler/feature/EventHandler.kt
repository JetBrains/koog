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
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolExecutionStartingContext
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
 *     onToolExecutionStarting { eventContext ->
 *         println("Tool called: ${eventContext.tool.name} with args ${eventContext.toolArgs}")
 *     }
 *
 *     onAgentCompleted { eventContext ->
 *         println("Agent finished with result: ${eventContext.result}")
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
     *     onToolExecutionStarting { eventContext ->
     *         println("Tool called: ${eventContext.tool.name} with args: ${eventContext.toolArgs}")
     *     }
     *
     *     onAgentCompleted { eventContext ->
     *         println("Agent finished with result: ${eventContext.result}")
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
                config.invokeOnAgentStarting(eventContext)
            }

            pipeline.interceptNodeExecutionStarting(interceptContext) intercept@{ eventContext: NodeExecutionStartingContext ->
                config.invokeOnNodeExecutionStarting(eventContext)
            }

            pipeline.interceptNodeExecutionCompleted(interceptContext) intercept@{ eventContext: NodeExecutionCompletedContext ->
                config.invokeOnNodeExecutionCompleted(eventContext)
            }

            pipeline.interceptNodeExecutionFailed(
                interceptContext
            ) intercept@{ eventContext: NodeExecutionFailedContext ->
                config.invokeOnNodeExecutionFailed(eventContext)
            }
        }

        private fun registerCommonPipelineHandlers(
            config: EventHandlerConfig,
            pipeline: AIAgentPipeline,
            interceptContext: InterceptContext<EventHandler>
        ) {
            pipeline.interceptAgentCompleted(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentCompleted(eventContext)
            }

            pipeline.interceptAgentExecutionFailed(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentExecutionFailed(eventContext)
            }

            pipeline.interceptAgentClosing(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentClosing(eventContext)
            }

            pipeline.interceptStrategyStarting(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyStarting(eventContext)
            }

            pipeline.interceptStrategyCompleted(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyCompleted(eventContext)
            }

            pipeline.interceptLLMCallStarting(interceptContext) intercept@{ eventContext: LLMCallStartingContext ->
                config.invokeOnLLMCallStarting(eventContext)
            }

            pipeline.interceptLLMCallCompleted(interceptContext) intercept@{ eventContext: LLMCallCompletedContext ->
                config.invokeOnLLMCallCompleted(eventContext)
            }

            pipeline.interceptToolExecutionStarting(interceptContext) intercept@{ eventContext: ToolExecutionStartingContext ->
                config.invokeOnToolExecutionStarting(eventContext)
            }

            pipeline.interceptToolValidationFailed(
                interceptContext
            ) intercept@{ eventContext: ToolValidationFailedContext ->
                config.invokeOnToolValidationFailed(eventContext)
            }

            pipeline.interceptToolExecutionFailed(interceptContext) intercept@{ eventContext: ToolExecutionFailedContext ->
                config.invokeOnToolExecutionFailed(eventContext)
            }

            pipeline.interceptToolExecutionCompleted(interceptContext) intercept@{ eventContext: ToolExecutionCompletedContext ->
                config.invokeOnToolExecutionCompleted(eventContext)
            }

            pipeline.interceptLLMStreamingStarting(interceptContext) intercept@{ eventContext: LLMStreamingStartingContext ->
                config.invokeOnLLMStreammingStarting(eventContext)
            }

            pipeline.interceptLLMStreamingFrameReceived(interceptContext) intercept@{ eventContext: LLMStreamingFrameReceivedContext ->
                config.invokeOnLLMStreamingFrameReceived(eventContext)
            }

            pipeline.interceptLLMStreamingFailed(interceptContext) intercept@{ eventContext ->
                config.invokeOnLLMStreamingFailed(eventContext)
            }

            pipeline.interceptLLMStreamingCompleted(interceptContext) intercept@{ eventContext: LLMStreamingCompletedContext ->
                config.invokeOnLLMStreamingCompleted(eventContext)
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
 * @param configure A lambda with a receiver that configures the EventHandlerConfig.
 *                  Use this to set up handlers for specific events.
 *
 * Example:
 * ```
 * handleEvents {
 *     // Log when tools are called
 *     onToolExecutionStarting { eventContext ->
 *         println("Tool called: ${eventContext.tool.name} with args: ${eventContext.toolArgs}")
 *     }
 *
 *     // Handle errors
 *     onAgentExecutionFailed { eventContext ->
 *         logger.error("Agent error: ${eventContext.throwable.message}")
 *     }
 * }
 * ```
 */
public fun FeatureContext.handleEvents(configure: EventHandlerConfig.() -> Unit) {
    install(EventHandler) {
        configure()
    }
}
