package ai.koog.rag.base.storage

public data class SimilaritySearchRequest(
    val query: String,
    override val limit: Int = 10,
    override val similarityThreshold: Double = 0.0,
) : SearchRequest
