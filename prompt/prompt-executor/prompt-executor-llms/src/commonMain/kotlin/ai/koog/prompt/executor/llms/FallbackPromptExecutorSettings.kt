package ai.koog.prompt.executor.llms

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

/**
 * Represents configuration for a fallback large language model (LLM) execution strategy.
 *
 * This class is used to specify a fallback LLM provider and model that can be utilized
 * when the primary LLM execution fails or when no client is available for the requested model.
 * It ensures that the fallback model is associated with the specified fallback provider.
 *
 * @property fallbackProvider The LLMProvider responsible for handling fallback requests.
 * @property fallbackModel The LLModel instance to be used for fallback execution.
 *
 * @throws IllegalArgumentException If the provider of the fallback model does not match the
 * fallback provider.
 */
public data class FallbackPromptExecutorSettings(
    val fallbackProvider: LLMProvider,
    val fallbackModel: LLModel
) {
    init {
        check(fallbackModel.provider == fallbackProvider) {
            "LLM model provider must match the fallback provider"
        }
    }
}
