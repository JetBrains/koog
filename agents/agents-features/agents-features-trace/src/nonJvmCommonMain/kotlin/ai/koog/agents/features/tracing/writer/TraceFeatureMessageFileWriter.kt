package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.writer.FeatureMessageFileWriter
import kotlinx.io.Sink
import kotlin.jvm.JvmOverloads

/**
 * A specialized implementation of [FeatureMessageFileWriter] designed for handling trace-related feature messages.
 * This class facilitates writing trace-specific feature messages to a file, supporting custom formatting logic if needed.
 *
 * @param Path The type representing the file path supported by the file system provider.
 * @param targetPath The file where feature messages will be written.
 * @param sinkOpener A lambda function that returns a [Sink] for writing to the file. The lifecycle of the sink is managed by this class.
 * @param format An optional lambda function for custom formatting of [FeatureMessage] instances into their string representations.
 * This can be used to apply specific serialization logic if required. If not provided, the default message trace representation is used.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class TraceFeatureMessageFileWriter<Path> @JvmOverloads actual constructor(
    targetPath: Path,
    sinkOpener: (Path) -> Sink,
    format: ((FeatureMessage) -> String)?
) : FeatureMessageFileWriter<Path>(targetPath, sinkOpener) {

    private val delegate = TraceFeatureMessageFileWriterImpl(targetPath, sinkOpener, format)

    actual override fun FeatureMessage.toFileString(): String = with(delegate) { toFileString() }
}
