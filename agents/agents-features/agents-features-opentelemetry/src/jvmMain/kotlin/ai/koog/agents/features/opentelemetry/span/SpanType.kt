package ai.koog.agents.features.opentelemetry.span

internal enum class SpanType {
    CREATE_AGENT,
    INVOKE_AGENT,
    STRATEGY,
    NODE,
    SUBGRAPH,
    INFERENCE,
    EXECUTE_TOOL,
    PLAN_CREATION,
    STEP_EXECUTION,
    PLAN_COMPLETION_EVALUATION,
}
