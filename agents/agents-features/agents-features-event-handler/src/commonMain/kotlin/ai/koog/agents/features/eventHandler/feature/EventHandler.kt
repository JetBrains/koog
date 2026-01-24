@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFunctionalFeature
import ai.koog.agents.core.feature.AIAgentGraphFeature
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionStartingContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingCompletedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingFrameReceivedContext
import ai.koog.agents.core.feature.handler.streaming.LLMStreamingStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionFailedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallFailedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolValidationFailedContext
import ai.koog.agents.core.feature.pipeline.AIAgentFunctionalPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentGraphPipeline
import ai.koog.agents.core.feature.pipeline.AIAgentPipeline
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
 *     onToolCallStarting { eventContext ->
 *         println("Tool called: ${eventContext.toolName} with args ${eventContext.toolArgs}")
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
     * Companion object implementing agent feature, handling [EventHandler] creation and installation.
     */
    public companion object Feature :
        AIAgentGraphFeature<EventHandlerConfig, EventHandler>,
        AIAgentFunctionalFeature<EventHandlerConfig, EventHandler> {

        private val logger = KotlinLogging.logger { }

        override val key: AIAgentStorageKey<EventHandler> =
            AIAgentStorageKey("agents-features-event-handler")

        override fun createInitialConfig(): EventHandlerConfig = EventHandlerConfig()

        override fun install(
            config: EventHandlerConfig,
            pipeline: AIAgentGraphPipeline,
        ): EventHandler {
            logger.info { "Start installing feature: ${EventHandler::class.simpleName}" }

            val eventHandler = EventHandler()

            registerCommonPipelineHandlers(config, pipeline)
            registerGraphPipelineHandlers(config, pipeline)

            return eventHandler
        }

        override fun install(
            config: EventHandlerConfig,
            pipeline: AIAgentFunctionalPipeline,
        ): EventHandler {
            val eventHandler = EventHandler()

            registerCommonPipelineHandlers(config, pipeline)

            return eventHandler
        }

        private fun registerGraphPipelineHandlers(
            config: EventHandlerConfig,
            pipeline: AIAgentGraphPipeline,
        ) {
            pipeline.interceptAgentStarting(this) intercept@{ eventContext ->
                config.invokeOnAgentStarting(eventContext)
            }

            pipeline.interceptNodeExecutionStarting(this) intercept@{ eventContext: NodeExecutionStartingContext ->
                config.invokeOnNodeExecutionStarting(eventContext)
            }

            pipeline.interceptNodeExecutionCompleted(this) intercept@{ eventContext: NodeExecutionCompletedContext ->
                config.invokeOnNodeExecutionCompleted(eventContext)
            }

            pipeline.interceptNodeExecutionFailed(this) intercept@{ eventContext: NodeExecutionFailedContext ->
                config.invokeOnNodeExecutionFailed(eventContext)
            }

            pipeline.interceptSubgraphExecutionStarting(this) intercept@{ eventContext: SubgraphExecutionStartingContext ->
                config.invokeOnSubgraphExecutionStarting(eventContext)
            }

            pipeline.interceptSubgraphExecutionCompleted(this) intercept@{ eventContext: SubgraphExecutionCompletedContext ->
                config.invokeOnSubgraphExecutionCompleted(eventContext)
            }

            pipeline.interceptSubgraphExecutionFailed(this) intercept@{ eventContext: SubgraphExecutionFailedContext ->
                config.invokeOnSubgraphExecutionFailed(eventContext)
            }
        }

        private fun registerCommonPipelineHandlers(
            config: EventHandlerConfig,
            pipeline: AIAgentPipeline,
        ) {
            pipeline.interceptAgentCompleted(this) intercept@{ eventContext ->
                config.invokeOnAgentCompleted(eventContext)
            }

            pipeline.interceptAgentExecutionFailed(this) intercept@{ eventContext ->
                config.invokeOnAgentExecutionFailed(eventContext)
            }

            pipeline.interceptAgentClosing(this) intercept@{ eventContext ->
                config.invokeOnAgentClosing(eventContext)
            }

            pipeline.interceptStrategyStarting(this) intercept@{ eventContext ->
                config.invokeOnStrategyStarting(eventContext)
            }

            pipeline.interceptStrategyCompleted(this) intercept@{ eventContext ->
                config.invokeOnStrategyCompleted(eventContext)
            }

            pipeline.interceptLLMCallStarting(this) intercept@{ eventContext: LLMCallStartingContext ->
                config.invokeOnLLMCallStarting(eventContext)
            }

            pipeline.interceptLLMCallCompleted(this) intercept@{ eventContext: LLMCallCompletedContext ->
                config.invokeOnLLMCallCompleted(eventContext)
            }

            pipeline.interceptToolCallStarting(this) intercept@{ eventContext: ToolCallStartingContext ->
                config.invokeOnToolCallStarting(eventContext)
            }

            pipeline.interceptToolValidationFailed(
                this
            ) intercept@{ eventContext: ToolValidationFailedContext ->
                config.invokeOnToolValidationFailed(eventContext)
            }

            pipeline.interceptToolCallFailed(this) intercept@{ eventContext: ToolCallFailedContext ->
                config.invokeOnToolCallFailed(eventContext)
            }

            pipeline.interceptToolCallCompleted(this) intercept@{ eventContext: ToolCallCompletedContext ->
                config.invokeOnToolCallCompleted(eventContext)
            }

            pipeline.interceptLLMStreamingStarting(this) intercept@{ eventContext: LLMStreamingStartingContext ->
                config.invokeOnLLMStreamingStarting(eventContext)
            }

            pipeline.interceptLLMStreamingFrameReceived(this) intercept@{ eventContext: LLMStreamingFrameReceivedContext ->
                config.invokeOnLLMStreamingFrameReceived(eventContext)
            }

            pipeline.interceptLLMStreamingFailed(this) intercept@{ eventContext ->
                config.invokeOnLLMStreamingFailed(eventContext)
            }

            pipeline.interceptLLMStreamingCompleted(this) intercept@{ eventContext: LLMStreamingCompletedContext ->
                config.invokeOnLLMStreamingCompleted(eventContext)
            }
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
 *     onToolCallStarting { eventContext ->
 *         println("Tool called: ${eventContext.toolName} with args: ${eventContext.toolArgs}")
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
