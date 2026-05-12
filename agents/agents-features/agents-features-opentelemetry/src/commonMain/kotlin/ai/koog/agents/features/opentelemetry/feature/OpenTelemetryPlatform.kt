package ai.koog.agents.features.opentelemetry.feature

/**
 * Holds platform-specific state attached to an [OpenTelemetryConfig].
 *
 * Extension functions declared in `jvmMain` add to the config's public API without being able
 * to add properties; this object provides the backing storage for those extensions (JVM-side
 * span exporters held for shutdown, the `SdkMeterProvider`, metric exporters/filters/adapters).
 *
 * Non-JVM targets have nothing to store and nothing to shut down, so their actual is empty.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
internal expect class OpenTelemetryPlatform() {

    /**
     * Releases platform-specific resources. Called from [OpenTelemetryConfig.closeSdks] when
     * shutdown-on-agent-close is enabled, and from the process shutdown hook registered in
     * [OpenTelemetryConfig.initializeOpenTelemetry].
     */
    fun shutdown()
}
