package ai.koog.rag.base.storage

/**
 * Score metric semantics exposed by storage.
 */
public enum class ScoreMetric {
    COSINE_SIMILARITY,
    COSINE_DISTANCE,
    DOT_PRODUCT,
    EUCLIDEAN_DISTANCE,
    BM25,
    HYBRID,
    CUSTOM
}
