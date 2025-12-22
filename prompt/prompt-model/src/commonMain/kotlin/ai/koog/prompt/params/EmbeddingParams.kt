package ai.koog.prompt.params

import kotlinx.serialization.Serializable

/**
 * Parameters for embedding generation.
 *
 * This is an `open class` (not a `data class`) to allow provider-specific subclasses
 * (e.g., [ai.koog.prompt.executor.clients.google.GoogleEmbeddingParams]) to add additional
 * parameters while maintaining polymorphism. This mirrors the [LLMParams] pattern.
 *
 * @property dimensions Desired output embedding dimensions.
 *   Only applicable to models that support variable dimensions
 *   (models with [ai.koog.prompt.llm.LLMCapability.Embedding.Dimensions] capability).
 *   If null, uses model's default dimension.
 */
@Serializable
public open class EmbeddingParams(
    public val dimensions: Int? = null,
) {
    init {
        dimensions?.let {
            require(it > 0) { "dimensions must be > 0, but was $it" }
        }
    }

    /**
     * Creates a copy of this instance with the ability to modify the dimensions property.
     */
    public open fun copy(dimensions: Int? = this.dimensions): EmbeddingParams =
        EmbeddingParams(dimensions)

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is EmbeddingParams -> false
        else -> dimensions == other.dimensions
    }

    override fun hashCode(): Int = dimensions?.hashCode() ?: 0

    override fun toString(): String = "EmbeddingParams(dimensions=$dimensions)"
}

