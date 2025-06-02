package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent.FeatureContext
import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.AIAgentPipeline
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.ExperimentalUuidApi

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
 *     onToolCall = { stage, tool, toolArgs ->
 *         println("Tool called: ${tool.name} with args $toolArgs")
 *     }
 *     
 *     onAgentFinished = { strategyName, result ->
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
     *     onToolCall = { stage, tool, toolArgs ->
     *         println("Tool called: ${tool.name} with args $toolArgs")
     *     }
     *
     *     onAgentFinished = { strategyName, result ->
     *         println("Agent finished with result: $result")
     *     }
     * }
     */
    @OptIn(ExperimentalUuidApi::class)
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

            //region Intercept Agent Events

            pipeline.interceptBeforeAgentStarted(this, featureImpl) intercept@{
                config.onBeforeAgentStarted(strategy, agent)
            }

            pipeline.interceptAgentFinished(this, featureImpl) intercept@{ strategyName, result ->
                config.onAgentFinished(strategyName, result)
            }

            pipeline.interceptAgentRunError(this, featureImpl) intercept@{ strategyName, sessionUuid, throwable ->
                config.onAgentRunError(strategyName, sessionUuid, throwable)
            }

            //endregion Intercept Agent Events

            //region Intercept Strategy Events

            pipeline.interceptStrategyStarted(this, featureImpl) intercept@{
                config.onStrategyStarted(strategy)
            }

            pipeline.interceptStrategyFinished(this, featureImpl) intercept@{ result ->
                config.onStrategyFinished(strategy, result)
            }

            //endregion Intercept Strategy Events

            //region Intercept Node Events

            pipeline.interceptBeforeNode(
                this,
                featureImpl
            ) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any? ->
                config.onBeforeNode(node, context, input)
            }

            pipeline.interceptAfterNode(
                this,
                featureImpl
            ) intercept@{ node: AIAgentNodeBase<*, *>, context: AIAgentContextBase, input: Any?, output: Any? ->
                config.onAfterNode(node, context, input, output)
            }

            //endregion Intercept Node Events

            //region Intercept LLM Call Events

            pipeline.interceptBeforeLLMCall(this, featureImpl) intercept@{ prompt, tools, model, sessionUuid ->
                config.onBeforeLLMCall(prompt, tools, model, sessionUuid)
            }

            pipeline.interceptAfterLLMCall(this, featureImpl) intercept@{ prompt, tools, model, responses, sessionUuid ->
                config.onAfterLLMCall(prompt, tools, model, responses, sessionUuid)
            }

            //endregion Intercept LLM Call Events

            //region Intercept Tool Call Events

            pipeline.interceptToolCall(this, featureImpl) intercept@{ tool, toolArgs ->
                config.onToolCall(tool, toolArgs)
            }

            pipeline.interceptToolValidationError(this, featureImpl) intercept@{  tool, toolArgs, value ->
                config.onToolValidationError(tool, toolArgs, value)
            }

            pipeline.interceptToolCallFailure(this, featureImpl) intercept@{ tool, toolArgs, throwable ->
                config.onToolCallFailure(tool, toolArgs, throwable)
            }

            pipeline.interceptToolCallResult(this, featureImpl) intercept@{ tool, toolArgs, result ->
                config.onToolCallResult(tool, toolArgs, result)
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
 *     onToolCall = { stage, tool, toolArgs ->
 *         println("Tool called: ${tool.name}")
 *     }
 *     
 *     // Handle errors
 *     onAgentRunError = { strategyName, throwable ->
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
