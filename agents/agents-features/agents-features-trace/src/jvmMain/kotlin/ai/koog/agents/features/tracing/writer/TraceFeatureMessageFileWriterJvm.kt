package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Factory for creating [TraceFeatureMessageFileWriter] instances using standard Java I/O types.
 *
 * This avoids the need to use `kotlinx.io` types directly from Java code.
 *
 * Example usage from Java:
 * ```java
 * var writer = TraceFeatureFileWriters.create(Path.of("/path/to/trace.log"));
 * ```
 */
public object TraceFeatureMessageFileWriterJvm {

    /**
     * Creates a [TraceFeatureMessageFileWriter] that writes to the given [java.nio.file.Path]
     * using a custom [OutputStream] opener.
     *
     * @param targetPath The file path where trace events will be written.
     * @param streamOpener A function that opens an [OutputStream] for the given path.
     *                     Defaults to [Files.newOutputStream].
     * @param format Optional custom formatter for trace events.
     * @return A new [TraceFeatureMessageFileWriter] instance.
     */
    @JvmStatic
    @JvmOverloads
    public fun create(
        targetPath: Path,
        streamOpener: ((path: Path) -> OutputStream)? = null,
        format: ((FeatureMessage) -> String)? = null,
    ): TraceFeatureMessageFileWriter<Path> {
        val outputStreamOpener = streamOpener ?: { path -> Files.newOutputStream(path) }
        return TraceFeatureMessageFileWriter(
            targetPath = targetPath,
            sinkOpener = { path -> outputStreamOpener.invoke(path).asSink().buffered() },
            format = format,
        )
    }
}
