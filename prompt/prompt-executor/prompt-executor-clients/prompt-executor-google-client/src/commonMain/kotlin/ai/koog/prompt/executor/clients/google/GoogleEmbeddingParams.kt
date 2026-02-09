package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.params.EmbeddingParams

/**
 * Specifies how the embedding will be used, allowing the model to optimize quality for your use case.
 *
 * For retrieval scenarios, use [RETRIEVAL_QUERY] for queries and [RETRIEVAL_DOCUMENT] for documents.
 */
public enum class GoogleEmbeddingTaskType {
    /** Query for search/retrieval. Use RETRIEVAL_DOCUMENT for the document side. */
    RETRIEVAL_QUERY,

    /** Document for search/retrieval. */
    RETRIEVAL_DOCUMENT,

    /** Semantic textual similarity comparison. */
    SEMANTIC_SIMILARITY,

    /** Embeddings for classification tasks. */
    CLASSIFICATION,

    /** Embeddings for clustering tasks. */
    CLUSTERING,

    /** Query for question answering. Use RETRIEVAL_DOCUMENT for the document side. */
    QUESTION_ANSWERING,

    /** Query for fact verification. Use RETRIEVAL_DOCUMENT for the document side. */
    FACT_VERIFICATION,

    /** Query for code retrieval (Java/Python). Use RETRIEVAL_DOCUMENT for the document side. */
    CODE_RETRIEVAL_QUERY,
}

/**
 * Google-specific embedding parameters.
 *
 * @property dimensions Desired output embedding dimensions (mapped to `outputDimensionality`).
 *   Only applicable to models with [LLMCapability.Embedding.Dimensions].
 *   If null, uses model's default dimension.
 * @property taskType Specifies the intended use case for the embeddings.
 * @property title Document title (only valid with taskType=RETRIEVAL_DOCUMENT).
 */
public class GoogleEmbeddingParams(
    public val dimensions: Int? = null,
    public val taskType: GoogleEmbeddingTaskType? = null,
    public val title: String? = null,
) : EmbeddingParams() {

    init {
        dimensions?.let {
            require(it > 0) { "dimensions must be > 0, but was $it" }
        }
        title?.let {
            require(taskType == GoogleEmbeddingTaskType.RETRIEVAL_DOCUMENT) {
                "title parameter is only valid when taskType is RETRIEVAL_DOCUMENT"
            }
        }
    }

    public fun copy(
        dimensions: Int? = this.dimensions,
        taskType: GoogleEmbeddingTaskType? = this.taskType,
        title: String? = this.title
    ): GoogleEmbeddingParams = GoogleEmbeddingParams(dimensions, taskType, title)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is GoogleEmbeddingParams -> false
        else ->
            dimensions == other.dimensions &&
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
 *
 * If the params are already [GoogleEmbeddingParams], returns them as-is.
 * Otherwise, creates a new [GoogleEmbeddingParams] with default values.
 */
internal fun EmbeddingParams.toGoogleEmbeddingParams(): GoogleEmbeddingParams = when (this) {
    is GoogleEmbeddingParams -> this
    else -> GoogleEmbeddingParams()
}
