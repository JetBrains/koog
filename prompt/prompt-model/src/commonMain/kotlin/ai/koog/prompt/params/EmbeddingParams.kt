package ai.koog.prompt.params

import kotlinx.serialization.Serializable

/**
 * Base parameters for embedding generation.
 *
 * This is an interface to allow provider-specific subclasses to add additional
 * parameters while maintaining polymorphism.
 *
 * @see LLMParams for the equivalent pattern used in completion/chat models.
 */
public interface EmbeddingParams {

    /**
     * Default implementation of EmbeddingParams for cases where
     * provider-specific parameters are not needed.
     */
    @Serializable
    public object None : EmbeddingParams
}
