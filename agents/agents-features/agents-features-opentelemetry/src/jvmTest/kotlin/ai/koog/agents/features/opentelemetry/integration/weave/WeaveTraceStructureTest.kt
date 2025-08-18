package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.integration.TraceStructureTestBase
import kotlin.test.Ignore

/**
 * A test class for verifying trace structures using the Weave exporter.
 */
// Explicitly ignore this test as we do not have env variables for Weave in CI to make these tests passed.
// Required env variables:
//   - WEAVE_ENTITY
//   - WEAVE_API_KEY
@Ignore
class WeaveTraceStructureTest :
    TraceStructureTestBase(openTelemetryConfigurator = { addWeaveExporter(verbose = this.isVerbose) })
