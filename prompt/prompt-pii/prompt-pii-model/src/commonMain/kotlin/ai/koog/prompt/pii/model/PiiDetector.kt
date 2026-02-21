package ai.koog.prompt.pii.model

/**
 * Detects PII spans in plain text.
 */
public interface PiiDetector {
    /**
     * Returns detected PII spans for [text].
     */
    public suspend fun detect(text: String): List<PiiDetection>
}

