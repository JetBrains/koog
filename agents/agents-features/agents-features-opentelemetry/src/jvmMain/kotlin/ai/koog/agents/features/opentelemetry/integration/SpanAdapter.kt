package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan

/**
 * Adapter abstract class for post-processing GenAI agent spans.
 *
 * This class allows customization of how GenAI agent spans are processed after they are created.
 * Implementations can modify span data, add additional attributes or events, or perform any other
 * post-processing logic needed before the span is completed.
 *
 * The abstract class provides a single method called after a span is created but before it is finished.
 */
internal abstract class SpanAdapter {

    /**
     * Invoked before the specified GenAIAgentSpan is started. This method allows implementations to
     * perform any setup or customization required prior to the span being initialized and used.
     *
     * @param span The GenAI agent span to process
     */
    open fun onBeforeSpanStarted(span: GenAIAgentSpan) { }

    /**
     * Invoked before the specified GenAIAgentSpan is finished. This method allows implementations
     * to perform any final processing, modifications, or cleanup tasks required before the span
     * is completed.
     *
     * @param span The GenAI span to process before it is finished.
     */
    open fun onBeforeSpanFinished(span: GenAIAgentSpan) { }
}
