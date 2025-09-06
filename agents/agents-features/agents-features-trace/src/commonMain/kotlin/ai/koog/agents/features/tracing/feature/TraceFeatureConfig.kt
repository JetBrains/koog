package ai.koog.agents.features.tracing.feature

import ai.koog.agents.core.feature.config.FeatureConfig

/**
 * Configuration for the tracing feature.
 *
 * This class allows you to configure how the tracing feature behaves, including
 * - Which message processors receive trace events
 *
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     install(Tracing) {
 *         // Add message processors to handle trace events
 *         addMessageProcessor(TraceFeatureMessageLogWriter(logger))
 *         addMessageProcessor(TraceFeatureMessageFileWriter(outputFile, fileSystem::sink))
 *     }
 * }
 * ```
 */
public class TraceFeatureConfig() : FeatureConfig()
