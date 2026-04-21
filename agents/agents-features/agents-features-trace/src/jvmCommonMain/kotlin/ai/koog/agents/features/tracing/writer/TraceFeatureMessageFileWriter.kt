package ai.koog.agents.features.tracing.writer

import ai.koog.agents.annotations.JavaAPI
import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.writer.FeatureMessageFileWriter
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path as JavaPath
import kotlinx.io.files.Path as KotlinPath

/**
 * A specialized implementation of [FeatureMessageFileWriter] for tracing and writing feature messages
 * to a target file, with customizable formatting and sink handling.
 *
 * This class supports writing feature messages to a file using a specified sink opener
 * and an optional formatting function. It serves as an adapter for delegating the core
 * functionality to [TraceFeatureMessageFileWriterImpl].
 *
 * @param targetPath The path where feature messages will be written.
 * @param sinkOpener A function responsible for opening a [Sink] for file writing. This function ensures
 *                   the lifecycle of the sink is properly managed. A default sink opener is available if not provided.
 * @param format An optional lambda function for customizing the formatting of [FeatureMessage] instances
 *               before writing them to the file. If not provided, a default formatting strategy is used.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class TraceFeatureMessageFileWriter<Path> @JvmOverloads actual constructor(
    targetPath: Path,
    sinkOpener: (Path) -> Sink,
    format: ((FeatureMessage) -> String)?
) : FeatureMessageFileWriter<Path>(targetPath, sinkOpener) {

    private val delegate = TraceFeatureMessageFileWriterImpl(targetPath, sinkOpener, format)

    actual override fun FeatureMessage.toFileString(): String = with(delegate) { toFileString() }

    /**
     * Companion object with factories for [TraceFeatureMessageFileWriter]
     * */
    public companion object {

        /**
         * Creates an instance of [TraceFeatureMessageFileWriter] for writing feature messages to a file.
         *
         * @param targetPath The path where feature messages will be written.
         * @param sinkOpener A lambda function that opens a [Sink] for writing files, managing its lifecycle.
         *                   If not provided, a default sink opener using the system file system will be used.
         * @param format Optional lambda function for custom formatting of [FeatureMessage] instances.
         *               If not provided, a default formatting strategy will be used.
         * @return A new instance of [TraceFeatureMessageFileWriter] configured with the specified parameters.
         */
        public fun create(
            targetPath: KotlinPath,
            sinkOpener: ((path: KotlinPath) -> Sink)? = null,
            format: ((FeatureMessage) -> String)? = null
        ): TraceFeatureMessageFileWriter<KotlinPath> {
            return TraceFeatureMessageFileWriter(
                targetPath = targetPath,
                sinkOpener = sinkOpener ?: { path -> SystemFileSystem.sink(path).buffered() },
                format = format
            )
        }

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
        @JavaAPI
        @JvmStatic
        @JvmOverloads
        public fun create(
            targetPath: JavaPath,
            streamOpener: ((path: JavaPath) -> OutputStream)? = null,
            format: ((FeatureMessage) -> String)? = null,
        ): TraceFeatureMessageFileWriter<JavaPath> {
            val outputStreamOpener = streamOpener ?: { path -> Files.newOutputStream(path) }
            return TraceFeatureMessageFileWriter(
                targetPath = targetPath,
                sinkOpener = { path -> outputStreamOpener.invoke(path).asSink().buffered() },
                format = format,
            )
        }
    }
}
