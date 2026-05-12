package ai.koog.agents.features.opentelemetry.feature

import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.feature.handler.AgentLifecycleEventContext
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import io.opentelemetry.kotlin.factory.ContextFactory

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
public actual class OpenTelemetryConfig internal actual constructor(
    internal actual val delegate: OpenTelemetryConfigImpl,
) : FeatureConfig(), OpenTelemetryConfigAPI by delegate {

    public actual constructor() : this(delegate = OpenTelemetryConfigImpl())

    internal actual val platform: OpenTelemetryPlatform
        get() = delegate.platform

    internal actual val contextFactory: ContextFactory
        get() = delegate.contextFactory

    internal actual val spanAdapter: SpanAdapter?
        get() = delegate.spanAdapter

    internal actual val instrumentationScopeName: String
        get() = delegate.instrumentationScopeName

    internal actual fun buildResourceMap(): Map<String, Any> = delegate.buildResourceMap()

    internal actual suspend fun closeSdks() {
        delegate.closeSdks()
    }

    override fun setEventFilter(filter: (AgentLifecycleEventContext) -> Boolean) {
        // Do not allow events filtering for the OpenTelemetry feature:
        // OpenTelemetry relies on the lifecycle hierarchy and dropped events would corrupt spans.
        throw UnsupportedOperationException("Events filtering is not allowed for the OpenTelemetry feature.")
    }
}
