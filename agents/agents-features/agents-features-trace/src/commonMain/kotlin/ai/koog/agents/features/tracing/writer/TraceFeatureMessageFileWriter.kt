package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.model.*
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
