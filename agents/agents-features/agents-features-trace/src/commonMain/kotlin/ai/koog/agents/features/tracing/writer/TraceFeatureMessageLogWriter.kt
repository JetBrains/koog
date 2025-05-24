package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.common.writer.FeatureMessageLogWriter
import io.github.oshai.kotlinlogging.KLogger

/**
 * A message processor that writes trace events to a logger.
 * 
 * This writer captures all trace events and writes them to the specified logger at the configured log level.
 * It formats each event type differently to provide clear and readable logs.
 * 
 * Tracing to logs is particularly useful for:
 * - Integration with existing logging infrastructure
 * - Real-time monitoring of agent behavior
 * - Filtering and searching trace events using log management tools
 * 
 * Example usage:
 * ```kotlin
 * // Create a logger
 * val logger = LoggerFactory.create("ai.koog.agents.tracing")
 * 
 * val agent = AIAgent(...) {
 *     install(Tracing) {
 *         // Write trace events to logs at INFO level (default)
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *         
 *         // Write trace events to logs at DEBUG level
 *         addMessageProcessor(TraceFeatureMessageLogWriter(
 *             targetLogger = logger,
 *             logLevel = LogLevel.DEBUG
 *         ))
 *         
 *         // Optionally provide custom formatting
 *         addMessageProcessor(TraceFeatureMessageLogWriter(
 *             targetLogger = logger,
 *             format = { message -> 
 *                 "[TRACE] ${message.eventId}: ${message::class.simpleName}"
 *             }
 *         ))
 *     }
 * }
 * ```
 * 
 * @param targetLogger The logger to write trace events to
 * @param logLevel The log level to use for trace events (default: INFO)
 * @param format Optional custom formatter for trace events
 */
public class TraceFeatureMessageLogWriter(
    targetLogger: KLogger,
    logLevel: LogLevel = LogLevel.INFO,
    private val format: ((FeatureMessage) -> String)? = null,
) : FeatureMessageLogWriter(targetLogger, logLevel) {

    internal companion object {
        val FeatureMessage.featureMessage
            get() = "Feature message"

        val FeatureEvent.featureEvent
            get() = "Feature message"

        val FeatureStringMessage.featureStringMessage
            get() = "Feature string message (message: ${this.message})"

        val AIAgentStartedEvent.agentStartedEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val AIAgentFinishedEvent.agentFinishedEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName}, result: ${this.result})"

        val AIAgentRunErrorEvent.agentRunErrorEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName}, error: ${this.error.message})"

        val AIAgentStrategyStartEvent.strategyStartEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName})"

        val AIAgentStrategyFinishedEvent.strategyFinishedEventFormat
            get() = "${this.eventId} (strategy name: ${this.strategyName}, result: ${this.result})"

        val LLMCallStartEvent.llmCallStartEventFormat
            get() = "${this.eventId} (prompt: ${this.prompt})"

        val LLMCallEndEvent.llmCallEndEventFormat
            get() = "${this.eventId} (response: ${this.response})"

        val LLMCallWithToolsStartEvent.llmCallWithToolsStartEventFormat
            get() = "${this.eventId} (prompt: ${this.prompt}, tools: [${this.tools.joinToString(", ")}])"

        val LLMCallWithToolsEndEvent.llmCallWithToolsEndEventFormat
            get() = "${this.eventId} (responses: ${this.responses}, tools: [${this.tools.joinToString(", ")}])"

        val ToolCallEvent.toolCallEventFormat
            get() = "${this.eventId} (tool: ${this.toolName}, tool args: ${this.toolArgs})"

        val ToolValidationErrorEvent.toolValidationErrorEventFormat
            get() = "${this.eventId} (tool: ${this.toolName}, tool args: ${this.toolArgs}, validation error: ${this.errorMessage})"

        val ToolCallFailureEvent.toolCallFailureEventFormat
            get() = "${this.eventId} (tool: ${this.toolName}, tool args: ${this.toolArgs}, error: ${this.error.message})"

        val ToolCallResultEvent.toolCallResultEventFormat
            get() = "${this.eventId} (tool: ${this.toolName}, tool args: ${this.toolArgs}, result: ${this.result})"

        val AIAgentNodeExecutionStartEvent.nodeExecutionStartEventFormat
            get() = "${this.eventId} (node: ${this.nodeName}, input: ${this.input})"

        val AIAgentNodeExecutionEndEvent.nodeExecutionEndEventFormat
            get() = "${this.eventId} (node: ${this.nodeName}, input: ${this.input}, output: ${this.output})"
    }

    override fun FeatureMessage.toLoggerMessage(): String {
        if (format != null) {
            return format.invoke(this)
        }

        return when (this) {
            is AIAgentStartedEvent            -> { this.agentStartedEventFormat }
            is AIAgentFinishedEvent           -> { this.agentFinishedEventFormat }
            is AIAgentRunErrorEvent           -> { this.agentRunErrorEventFormat}
            is AIAgentStrategyStartEvent      -> { this.strategyStartEventFormat }
            is AIAgentStrategyFinishedEvent   -> { this.strategyFinishedEventFormat }
            is LLMCallStartEvent              -> { this.llmCallStartEventFormat}
            is LLMCallEndEvent                -> { this.llmCallEndEventFormat}
            is LLMCallWithToolsStartEvent     -> { this.llmCallWithToolsStartEventFormat }
            is LLMCallWithToolsEndEvent       -> { this.llmCallWithToolsEndEventFormat }
            is ToolCallEvent                  -> { this.toolCallEventFormat }
            is ToolValidationErrorEvent       -> { this.toolValidationErrorEventFormat }
            is ToolCallFailureEvent           -> { this.toolCallFailureEventFormat }
            is ToolCallResultEvent            -> { this.toolCallResultEventFormat }
            is AIAgentNodeExecutionStartEvent -> { this.nodeExecutionStartEventFormat }
            is AIAgentNodeExecutionEndEvent   -> { this.nodeExecutionEndEventFormat }
            is FeatureStringMessage           -> { this.featureStringMessage }
            is FeatureEvent                   -> { this.featureEvent }
            else                              -> { this.featureMessage }
        }
    }
}
