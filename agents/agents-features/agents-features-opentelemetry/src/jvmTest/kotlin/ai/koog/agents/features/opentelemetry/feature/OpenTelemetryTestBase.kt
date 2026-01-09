package ai.koog.agents.features.opentelemetry.feature

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter

abstract class OpenTelemetryTestBase {

    protected fun createCustomSdk(exporter: SpanExporter): OpenTelemetrySdk {
        val builder = OpenTelemetrySdk.builder()

        val traceProviderBuilder = SdkTracerProvider
            .builder()
            .addSpanProcessor(SimpleSpanProcessor.builder(exporter).build())

        val sdk = builder
            .setTracerProvider(traceProviderBuilder.build())
            .build()

        return sdk
    }
}
