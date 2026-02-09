package ai.koog.prompt.params

import kotlinx.serialization.Serializable

/**
 * Base parameters for embedding generation.
 *
 * This is an `open class` to allow provider-specific subclasses to add additional
 * parameters while maintaining polymorphism. This mirrors the [LLMParams] pattern.
 *
 * @see LLMParams for the equivalent pattern used in completion/chat models.
 */
@Serializable
public open class EmbeddingParams {

    override fun equals(other: Any?): Boolean = other is EmbeddingParams

    override fun hashCode(): Int = 0

    override fun toString(): String = "EmbeddingParams()"
}
