package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.params.EmbeddingParams
import kotlinx.serialization.Serializable

/**
 * Task type for Google embedding API.
 * Specifies the intended use case to help the model produce better embeddings.
 *
 * **Polymorphic Usage**: Users can call `embed()` with either:
 * - Generic `EmbeddingParams(dimensions = 256)` - works with any provider
 * - Specific `GoogleEmbeddingParams(dimensions = 256, taskType = RETRIEVAL_QUERY)` - Google-specific features
 *
 * The conversion function [toGoogleEmbeddingParams] handles both cases transparently.
 */
@Serializable
public enum class GoogleEmbeddingTaskType(public val apiValue: String) {
    /** Query for search/retrieval. Use RETRIEVAL_DOCUMENT for the document side. */
    RETRIEVAL_QUERY("RETRIEVAL_QUERY"),
    
    /** Document for search/retrieval. */
    RETRIEVAL_DOCUMENT("RETRIEVAL_DOCUMENT"),
    
    /** Semantic textual similarity comparison. */
    SEMANTIC_SIMILARITY("SEMANTIC_SIMILARITY"),
    
    /** Embeddings for classification tasks. */
    CLASSIFICATION("CLASSIFICATION"),
    
    /** Embeddings for clustering tasks. */
    CLUSTERING("CLUSTERING"),
    
    /** Query for question answering. Use RETRIEVAL_DOCUMENT for the document side. */
    QUESTION_ANSWERING("QUESTION_ANSWERING"),
    
    /** Query for fact verification. Use RETRIEVAL_DOCUMENT for the document side. */
    FACT_VERIFICATION("FACT_VERIFICATION"),
    
    /** Query for code retrieval (Java/Python). Use RETRIEVAL_DOCUMENT for the document side. */
    CODE_RETRIEVAL_QUERY("CODE_RETRIEVAL_QUERY"),
}

/**
 * Google-specific embedding parameters.
 *
 * @property dimensions Desired output embedding dimensions (mapped to `outputDimensionality`).
 * @property taskType Specifies the intended use case for the embeddings.
 * @property title Document title (only valid with taskType=RETRIEVAL_DOCUMENT).
 */
public class GoogleEmbeddingParams(
    dimensions: Int? = null,
    public val taskType: GoogleEmbeddingTaskType? = null,
    public val title: String? = null,
) : EmbeddingParams(dimensions) {

    init {
        // title is only valid with RETRIEVAL_DOCUMENT
        if (title != null) {
            require(taskType == GoogleEmbeddingTaskType.RETRIEVAL_DOCUMENT) {
                "title parameter is only valid when taskType is RETRIEVAL_DOCUMENT"
            }
        }
    }

    override fun copy(dimensions: Int?): GoogleEmbeddingParams =
        GoogleEmbeddingParams(dimensions, taskType, title)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is GoogleEmbeddingParams -> false
        else -> dimensions == other.dimensions &&
                taskType == other.taskType &&
                title == other.title
    }

    override fun hashCode(): Int {
        var result = dimensions?.hashCode() ?: 0
        result = 31 * result + (taskType?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "GoogleEmbeddingParams(dimensions=$dimensions, taskType=$taskType, title=$title)"
}

/**
 * Converts generic [EmbeddingParams] to [GoogleEmbeddingParams].
 * Follows the same pattern as [LLMParams.toGoogleParams].
 */
internal fun EmbeddingParams.toGoogleEmbeddingParams(): GoogleEmbeddingParams = when (this) {
    is GoogleEmbeddingParams -> this
    else -> GoogleEmbeddingParams(dimensions = dimensions)
}

