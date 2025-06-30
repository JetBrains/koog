package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.model.*
import ai.koog.agents.core.feature.traceString
import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.features.common.writer.FeatureMessageFileWriter
import kotlinx.io.Sink

/**
 * A message processor that writes trace events to a file.
 * 
 * This writer captures all trace events and writes them to a specified file using the provided file system.
 * It formats each event type differently to provide clear and readable logs.
 * 
 * Tracing to files is particularly useful for:
 * - Persistent logging that survives application restarts
 * - Detailed analysis of agent behavior after execution
 * - Sharing trace logs with other developers or systems
 * 
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     install(Tracing) {
 *         // Write trace events to a file
 *         addMessageProcessor(TraceFeatureMessageFileWriter(
 *             sinkOpener = fileSystem::sink,
 *             targetPath = "agent-traces.log"
 *         ))
 *         
 *         // Optionally provide custom formatting
 *         addMessageProcessor(TraceFeatureMessageFileWriter(
 *             sinkOpener = fileSystem::sink,
 *             targetPath = "custom-traces.log",
 *             format = { message -> 
 *                 "[TRACE] ${message.eventId}: ${message::class.simpleName}"
 *             }
 *         ))
 *     }
 * }
 * ```
 * 
 * @param Path The type representing file paths in the file system
 * @param targetPath The path where feature messages will be written.
 * @param sinkOpener Returns a [Sink] for writing to the file, this class manages its lifecycle.
 * @param format Optional custom formatter for trace events
 */
public class TraceFeatureMessageFileWriter<Path>(
    targetPath: Path,
    sinkOpener: (Path) -> Sink,
    private val format: ((FeatureMessage) -> String)? = null,
) : FeatureMessageFileWriter<Path>(targetPath, sinkOpener) {

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

    override fun FeatureMessage.toFileString(): String {
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
