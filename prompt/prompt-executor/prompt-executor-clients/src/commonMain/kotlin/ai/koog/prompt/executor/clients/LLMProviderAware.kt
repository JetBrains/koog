package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Common interface for components that are associated with a specific [LLMProvider].
 *
 * Implemented by both [LLMClientAPI] and [LLMEmbeddingProvider] to enable shared
 * provider-validation logic (e.g. [requireMatchingProvider]).
 */
public interface LLMProviderAware {
    /**
     * Returns the [LLMProvider] associated with this component.
     */
    public fun llmProvider(): LLMProvider
}

/**
 * Ensures that the provided [LLModel] is associated with the same [LLMProvider] as the current component.
 * Throws an [IllegalArgumentException] if the providers do not match.
 *
 * @param model The [LLModel] whose provider will be validated against the component's provider.
 */
@InternalLLMClientApi
public fun LLMProviderAware.requireMatchingProvider(model: LLModel) {
    require(model.provider == llmProvider()) {
        "Model provider mismatch: ${model.id}.provider=${model.provider}, " +
            "${this::class.simpleName ?: "(LLMProviderAware)"}.llmProvider=${llmProvider()}"
    }
}
