package ai.koog.rag.base.storage.search

/**
 * Score metric semantics exposed by storage.
 *
 * Defines how similarity or relevance scores are computed and interpreted when querying a vector or document store.
 */
public enum class ScoreMetric {
    /**
     * Cosine similarity between two vectors, ranging from -1 to 1.
     * A value of 1 indicates identical direction, 0 indicates orthogonality, and -1 indicates opposite direction.
     * Higher values mean greater similarity.
     */
    COSINE_SIMILARITY,

    /**
     * Cosine distance between two vectors, defined as `1 - cosine_similarity`, ranging from 0 to 2.
     * A value of 0 indicates identical vectors, while 2 indicates opposite vectors.
     * Lower values mean greater similarity.
     */
    COSINE_DISTANCE,

    /**
     * Dot product (inner product) of two vectors.
     * The value is unbounded and depends on vector magnitudes. Higher values indicate greater similarity
     * when vectors are normalized.
     */
    DOT_PRODUCT,

    /**
     * Euclidean (L2) distance between two vectors, ranging from 0 to infinity.
     * A value of 0 indicates identical vectors. Lower values mean greater similarity.
     */
    EUCLIDEAN_DISTANCE,

    /**
     * BM25 (Best Matching 25) text relevance score, commonly used in full-text search.
     * The value is non-negative and unbounded. Higher values indicate greater relevance.
     */
    BM25,

    /**
     * A hybrid score combining multiple retrieval strategies (e.g., vector similarity and keyword search).
     * The range and interpretation depend on the specific combination and normalization used by the storage implementation.
     */
    HYBRID,

    /**
     * A custom scoring metric defined by the storage implementation.
     * The range and interpretation are implementation-specific.
     */
    CUSTOM
}
