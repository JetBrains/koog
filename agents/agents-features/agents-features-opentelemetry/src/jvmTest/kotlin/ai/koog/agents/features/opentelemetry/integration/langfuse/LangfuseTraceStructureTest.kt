package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase
import kotlin.test.Ignore

/**
 * A test class for verifying trace structures using the Langfuse exporter.
 */
// Explicitly ignore this test as we do not have env variables for Langfuse in CI to make these tests passed.
// Required env variables:
//   - LANGFUSE_SECRET_KEY
//   - LANGFUSE_PUBLIC_KEY
//   - LANGFUSE_HOST
@Ignore
class LangfuseTraceStructureTest :
    TraceStructureTestBase(openTelemetryConfigurator = { addLangfuseExporter() })
