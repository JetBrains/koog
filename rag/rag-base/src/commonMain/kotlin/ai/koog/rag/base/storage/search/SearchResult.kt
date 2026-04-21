package ai.koog.rag.base.storage.search

import kotlinx.serialization.json.JsonObject

/**
 * Represents a single result returned from a storage search operation.
 *
 * @param Document the type of the stored document
 * @property document the matched document content
 * @property score relevance score indicating how well the document matches the search query
 * @property id unique identifier of the matched document in the storage
 * @property metadata optional JSON metadata associated with the document
 * @property namespace optional namespace the document belongs to, used for logical isolation
 */
public data class SearchResult<Document>(
    val document: Document,
    val score: Score,
    val id: String? = null,
    val metadata: JsonObject? = null,
    val namespace: String? = null,
)

/**
 * Represents a relevance score for a search result.
 *
 * @property value the numeric score value
 * @property metric the metric used to compute this score (e.g., cosine similarity, dot product)
 */
public data class Score(
    val value: Double,
    val metric: ScoreMetric
)
