package ai.koog.agents.features.tracing.writer

import ai.koog.agents.core.feature.message.FeatureMessage
import ai.koog.agents.core.feature.writer.FeatureMessageLogWriter.LogLevel
import io.github.oshai.kotlinlogging.slf4j.toKLogger
import org.slf4j.Logger
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Factory for creating [TraceFeatureMessageLogWriter] instances using a standard SLF4J [Logger].
 *
 * This avoids the need to use `KotlinLogging` or `KLogger` directly from Java code.
 *
 * Example usage from Java:
 * ```java
 * var logger = LoggerFactory.getLogger("tracing");
 * var writer = TraceFeatureLogWriters.create(logger);
 * ```
 */
public object TraceFeatureMessageLogWriterJvm {

    /**
     * Creates a [TraceFeatureMessageLogWriter] that writes trace events to the given SLF4J [Logger].
     *
     * @param logger The SLF4J logger to write trace events to.
     * @param logLevel The log level to use for trace events (default: INFO).
     * @param format Optional custom formatter for trace events.
     * @return A new [TraceFeatureMessageLogWriter] instance.
     */
    @JvmStatic
    @JvmOverloads
    public fun create(
        logger: Logger,
        logLevel: LogLevel = LogLevel.INFO,
        format: ((FeatureMessage) -> String)? = null,
    ): TraceFeatureMessageLogWriter {
        return TraceFeatureMessageLogWriter(
            targetLogger = logger.toKLogger(),
            logLevel = logLevel,
            format = format,
        )
    }
}
