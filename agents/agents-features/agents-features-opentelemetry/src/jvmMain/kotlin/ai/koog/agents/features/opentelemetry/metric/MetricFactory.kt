package ai.koog.agents.features.opentelemetry.metric

internal object MetricFactory {

    /**
     * Build and register a Counter instrument for tracking GenAI token usage.
     *
     * The instrument name, description, and unit are taken from [GenAIMetrics.Client.Token.Usage].
     * This counter is created according to the OpenTelemetry Metrics API. It is pre-initialized with
     * a zero value to make the instrument visible to exporters even if no data points were recorded yet.
     *
     * Metric attributes when recording values (per GenAI semantic conventions):
     * - gen_ai.operation.name (required)
     * - gen_ai.provider.name (required)
     * - gen_ai.token.type (required) — INPUT or OUTPUT
     * - gen_ai.request.model (conditionally required)
     * - gen_ai.response.model (recommended)
     * - server.address (recommended)
     * - server.port (conditionally required, if server.address is set)
     */
    internal fun createTokenCounterMetric(): CounterMetric = object : CounterMetric {
        override val name: String = GenAIMetrics.Client.Token.Usage.name
        override val description: String = GenAIMetrics.Client.Token.Usage.description
        override val unit: String = GenAIMetrics.Client.Token.Usage.unit
    }

    /**
     * Build and register a Counter instrument for counting tool calls performed by the agent.
     *
     * This is a Koog-specific metric (not part of the GenAI semantic conventions).
     * The instrument name, description, and unit are taken from [KoogMetrics.Client.Tool.Call.Count].
     * This counter is created according to the OpenTelemetry Metrics API and pre-initialized with a
     * zero value to ensure the instrument appears in the exporter even without recorded data points.
     *
     * Metric attributes when recording values:
     * - gen_ai.operation.name (required) — set to "execute_tool"
     * - gen_ai.tool.name (recommended)
     * - koog.tool.call.status (recommended)
     */
    internal fun createToolCallCounterMetric(): CounterMetric = object : CounterMetric {
        override val name: String = KoogMetrics.Client.Tool.Call.Count.name
        override val description: String = KoogMetrics.Client.Tool.Call.Count.description
        override val unit: String = KoogMetrics.Client.Tool.Call.Count.unit
    }

    /**
     * Build and register a Histogram instrument for measuring GenAI operation durations.
     *
     * The instrument name, description, and unit are taken from [GenAIMetrics.Client.Operation.Duration].
     * This metric SHOULD be specified with ExplicitBucketBoundaries of
     * [0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92] seconds
     * to provide a meaningful latency distribution for operation durations.
     *
     * Metric attributes when recording values (per GenAI semantic conventions):
     * - gen_ai.operation.name (required)
     * - gen_ai.provider.name (required)
     * - error.type (conditionally required, if operation ended in error)
     * - gen_ai.request.model (conditionally required)
     * - gen_ai.response.model (recommended)
     * - server.address (recommended)
     * - server.port (conditionally required, if server.address is set)
     */
    internal fun createOperationDurationHistogramMetric(): HistogramMetric = object : HistogramMetric {
        override val name: String = GenAIMetrics.Client.Operation.Duration.name
        override val description: String = GenAIMetrics.Client.Operation.Duration.description
        override val unit: String = GenAIMetrics.Client.Operation.Duration.unit

        override val boundariesAdvice: List<Double> = listOf(
            0.01, 0.02, 0.04, 0.08, 0.16, 0.32, 0.64, 1.28, 2.56, 5.12, 10.24, 20.48, 40.96, 81.92
        )
    }
}
