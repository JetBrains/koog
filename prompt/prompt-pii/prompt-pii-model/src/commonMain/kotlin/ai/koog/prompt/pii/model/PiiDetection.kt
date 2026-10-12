package ai.koog.prompt.pii.model

/**
 * A typed PII detection within source text.
 */
public data class PiiDetection(
    public val start: Int,
    public val endExclusive: Int,
    public val type: PiiType,
) {
    init {
        require(start >= 0) { "start must be >= 0, got $start" }
        require(endExclusive > start) { "endExclusive must be > start, got [$start, $endExclusive)" }
    }
}
