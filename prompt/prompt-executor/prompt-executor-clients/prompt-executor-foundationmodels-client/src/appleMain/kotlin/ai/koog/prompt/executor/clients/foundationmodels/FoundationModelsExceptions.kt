package ai.koog.prompt.executor.clients.foundationmodels

/**
 * Errors raised by [FoundationModelsLLMClient].
 */
public sealed class FoundationModelsException(message: String) : Exception(message) {
    /** The on-device model is not usable (e.g. device ineligible, Apple Intelligence off, model not downloaded). */
    public class Unavailable(public val reason: String) :
        FoundationModelsException("Foundation Models is unavailable: $reason")

    /** Generation failed inside the Foundation Models framework. */
    public class Generation(public val detail: String) :
        FoundationModelsException("Foundation Models generation failed: $detail")
}
