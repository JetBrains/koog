package ai.koog.prompt.executor.clients.foundationmodels

/**
 * Errors raised by [FoundationModelsLLMClient].
 */
public sealed class FoundationModelsException(message: String) : Exception(message) {
    /** The on-device model is not usable right now; [availability] carries the typed reason. */
    public class Unavailable(public val availability: FoundationModelsAvailability.Unavailable) :
        FoundationModelsException("Foundation Models is unavailable: $availability")

    /** Generation failed inside the Foundation Models framework. */
    public class Generation(public val detail: String) :
        FoundationModelsException("Foundation Models generation failed: $detail")
}
