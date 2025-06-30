package ai.koog.agents.features.tracing.writer

import ai.koog.agents.features.common.message.FeatureMessage
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
    override fun FeatureMessage.toFileString(): String {
        if (format != null) {
            return format.invoke(this)
        }

        return this.traceMessage
    }
}
