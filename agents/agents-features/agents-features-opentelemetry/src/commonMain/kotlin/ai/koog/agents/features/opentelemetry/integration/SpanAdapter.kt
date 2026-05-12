package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

/**
 * Adapter for post-processing GenAI agent spans.
 */
public abstract class SpanAdapter {

    /**
     * Invoked before [span] is started.
     *
     * @param span Span about to be started.
     */
    public open fun onBeforeSpanStarted(span: GenAIAgentSpan) { }

    /**
     * Invoked before [span] is finished.
     *
     * @param span Span about to be finished.
     */
    public open fun onBeforeSpanFinished(span: GenAIAgentSpan) { }
}
