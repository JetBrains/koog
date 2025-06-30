package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.core.feature.traceString
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.common.writer.FeatureMessageLogWriter
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.agentFinishedEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.agentRunErrorEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.agentStartedEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.featureEvent
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.featureMessage
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.featureStringMessage
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.llmCallEndEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.llmCallStartEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.nodeExecutionEndEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.nodeExecutionStartEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.strategyFinishedEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.strategyStartEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.toolCallEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.toolCallFailureEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.toolCallResultEventFormat
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageFileWriter.Companion.toolValidationErrorEventFormat
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
        internal val FeatureMessage.featureMessage
            get() = "Feature message"

        internal val FeatureEvent.featureEvent
            get() = "Feature event"

        internal val FeatureStringMessage.featureStringMessage
            get() = "Feature string message (message: ${message})"

        internal val AIAgentStartedEvent.agentStartedEventFormat
            get() = "$eventId (agent id: ${agentId}, session id: ${sessionId}, strategy: ${strategyName})"

        internal val AIAgentFinishedEvent.agentFinishedEventFormat
            get() = "$eventId (agent id: ${agentId}, session id: ${sessionId}, result: ${result})"

        internal val AIAgentRunErrorEvent.agentRunErrorEventFormat
            get() = "$eventId (agent id: ${agentId}, session id: ${sessionId}, error: ${error.message})"

        internal val AIAgentStrategyStartEvent.strategyStartEventFormat
            get() = "$eventId (session id: ${sessionId}, strategy: ${strategyName})"

        internal val AIAgentStrategyFinishedEvent.strategyFinishedEventFormat
            get() = "$eventId (session id: ${sessionId}, strategy: ${strategyName}, result: ${result})"

        internal val AIAgentNodeExecutionStartEvent.nodeExecutionStartEventFormat
            get() = "$eventId (session id: ${sessionId}, node: ${nodeName}, input: ${input})"

        internal val AIAgentNodeExecutionEndEvent.nodeExecutionEndEventFormat
            get() = "$eventId (session id: ${sessionId}, node: ${nodeName}, input: ${input}, output: ${output})"

        internal val BeforeLLMCallEvent.llmCallStartEventFormat
            get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, tools: [${tools.joinToString()}])"

        internal val AfterLLMCallEvent.llmCallEndEventFormat
            get() = "$eventId (session id: ${sessionId}, prompt: ${prompt.traceString}, model: ${model}, responses: [${responses.joinToString { it.traceString }}])"

        internal val ToolCallEvent.toolCallEventFormat
            get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs})"

        internal val ToolValidationErrorEvent.toolValidationErrorEventFormat
            get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, validation error: ${error})"

        internal val ToolCallFailureEvent.toolCallFailureEventFormat
            get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, error: ${error.message})"

        internal val ToolCallResultEvent.toolCallResultEventFormat
            get() = "$eventId (session id: ${sessionId}, tool: ${toolName}, tool args: ${toolArgs}, result: ${result})"
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
            is BeforeLLMCallEvent             -> { this.llmCallStartEventFormat}
            is AfterLLMCallEvent              -> { this.llmCallEndEventFormat}
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
