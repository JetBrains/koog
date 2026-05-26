package ai.koog.agents.features.opentelemetry.span

/**
 * Type tag for a [GenAIAgentSpan].
 */
public enum class SpanType {
    /**
     * Agent construction.
     */
    CREATE_AGENT,

    /**
     * One full agent run.
     */
    INVOKE_AGENT,

    /**
     * Strategy graph execution.
     */
    STRATEGY,

    /**
     * Single graph node execution.
     */
    NODE,

    /**
     * Subgraph execution.
     */
    SUBGRAPH,

    /**
     * One LLM inference call.
     */
    INFERENCE,

    /**
     * One tool call invocation.
     */
    EXECUTE_TOOL,
}
