package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import ai.koog.agents.core.feature.InterceptContext
import ai.koog.agents.core.feature.handler.*
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
    public companion object Feature : AIAgentFeature<EventHandlerConfig, EventHandler> {

        private val logger = KotlinLogging.logger {  }

        override val key: AIAgentStorageKey<EventHandler> =
            AIAgentStorageKey("agents-features-event-handler")

        override fun createInitialConfig(): EventHandlerConfig = EventHandlerConfig()

        override fun install(
            config: EventHandlerConfig,
            pipeline: AIAgentPipeline,
        ) {
            logger.info { "Start installing feature: ${EventHandler::class.simpleName}" }

            val featureImpl = EventHandler()
            val interceptContext = InterceptContext(this, featureImpl)

            //region Intercept Agent Events

            pipeline.interceptBeforeAgentStarted(interceptContext) intercept@{ eventContext ->
                config.invokeOnBeforeAgentStarted(eventContext)
            }

            pipeline.interceptAgentFinished(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentFinished(eventContext)
            }

            pipeline.interceptAgentRunError(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentRunError(eventContext)
            }

            pipeline.interceptAgentBeforeClosed(interceptContext) intercept@{ eventContext ->
                config.invokeOnAgentBeforeClose(eventContext)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyStarted(eventContext)
            }

            pipeline.interceptStrategyFinished(interceptContext) intercept@{ eventContext ->
                config.invokeOnStrategyFinished(eventContext)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(interceptContext) intercept@{ eventContext: NodeBeforeExecuteContext ->
                config.invokeOnBeforeNode(eventContext)
            }

            pipeline.interceptAfterNode(interceptContext) intercept@{ eventContext: NodeAfterExecuteContext ->
                config.invokeOnAfterNode(eventContext)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(interceptContext) intercept@{ eventContext: BeforeLLMCallContext ->
                config.invokeOnBeforeLLMCall(eventContext)
            }

            pipeline.interceptAfterLLMCall(interceptContext) intercept@{ eventContext: AfterLLMCallContext ->
                config.invokeOnAfterLLMCall(eventContext)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(interceptContext) intercept@{ eventContext: ToolCallContext ->
                config.invokeOnToolCall(eventContext)
            }

            pipeline.interceptToolValidationError(interceptContext) intercept@{ eventContext: ToolValidationErrorContext ->
                config.invokeOnToolValidationError(eventContext)
            }

            pipeline.interceptToolCallFailure(interceptContext) intercept@{ eventContext: ToolCallFailureContext ->
                config.invokeOnToolCallFailure(eventContext)
            }

            pipeline.interceptToolCallResult(interceptContext) intercept@{ eventContext: ToolCallResultContext ->
                config.invokeOnToolCallResult(eventContext)
            }

            //endregion Intercept Tool Call Events
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
