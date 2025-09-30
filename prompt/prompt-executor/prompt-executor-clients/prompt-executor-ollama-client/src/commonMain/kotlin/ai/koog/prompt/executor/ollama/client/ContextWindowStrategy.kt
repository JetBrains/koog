package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.tokenizer.PromptTokenizer
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger { }

/**
 * Represents a strategy for computing the context window length for `OllamaClient`.
 * Different implementations define specific approaches to computing the context window length.
 * Based on the context window length computed by this strategy, Ollama will truncate the context window accordingly.
 *
 * To decide the context window length, Ollama proceeds as follows:
 * - If a `num_ctx` parameter is specified in the chat request, the context window length is set to that value.
 * - If the model definition contains a `num_ctx` parameter, the context window length is set to that value.
 * - If an `OLLAMA_CONTEXT_LENGTH` environment variable is set, the context window length is set to that value.
 * - Otherwise, the context window length is set to the default value of 2048.
 *
 * Effectively, this strategy allows you to specify what `num_ctx` value will be set in chat requests sent to Ollama,
 * for a given prompt and model.
 *
 * Important: You will want to have a context window length that does not change often for a specific model.
 * Indeed, Ollama will reload the model every time the context window length changes.
 *
 * Example implementations:
 * - [ContextWindowStrategy.None]
 * - [ContextWindowStrategy.Fixed]
 * - [ContextWindowStrategy.FitPrompt]
 */
public interface ContextWindowStrategy {

    public fun computeContextLength(prompt: Prompt, model: LLModel): Long?

    public companion object {
        /**
         * A strategy for letting the Ollama server decide the context window length.
         * To decide the context window length, Ollama proceeds as follows:
         * - If the model definition contains a `num_ctx` parameter, the context window length is set to that value.
         * - If an `OLLAMA_CONTEXT_LENGTH` environment variable is set, the context window length is set to that value.
         * - Otherwise, the context window length is set to the default value of 2048.
         */
        public data object None : ContextWindowStrategy {
            override fun computeContextLength(prompt: Prompt, model: LLModel): Long? = null
        }

        /**
         * A strategy for specifying a fixed context window length.
         * If the given [contextLength] is more than the maximum context window length supported by the model,
         * the context window length will be set to the maximum context window length supported by the model.
         *
         * @param contextLength The context window length to use.
         */
        public data class Fixed(val contextLength: Long) : ContextWindowStrategy {
            override fun computeContextLength(prompt: Prompt, model: LLModel): Long {
                if (contextLength > model.contextLength) {
                    logger.warn {
                        "Context length $contextLength was more than what is supported by model '${model.id}'," +
                            " falling back to the model's maximum context length ${model.contextLength}"
                    }
                    return model.contextLength
                }
                return contextLength
            }
        }

        /**
         * A strategy for computing the context window length based on the prompt length.
         *
         * @param promptTokenizer The [PromptTokenizer] to use for computing the prompt length,
         *   or null to use the last reported token usage.
         * @param granularity The granularity to use for computing the context window length. Defaults to 2048.
         * @param minimumContextLength The minimum context window length,
         *   if the prompt length is less than it or cannot be computed yet.
         *   If not null, [minimumContextLength] must be a multiple of the [granularity].
         *   If null, we let Ollama decide the context window length.
         */
        public data class FitPrompt(
            val promptTokenizer: PromptTokenizer? = null,
            val granularity: Long = 2048,
            val minimumContextLength: Long? = null,
        ) : ContextWindowStrategy {

            init {
                require(granularity > 0) { "Granularity must be greater than 0" }
                require(minimumContextLength == null || minimumContextLength % granularity == 0L) {
                    "Minimum context length must be a multiple of granularity"
                }
            }

            override fun computeContextLength(prompt: Prompt, model: LLModel): Long? {
                val promptLength = when {
                    promptTokenizer != null -> promptTokenizer.tokenCountFor(prompt)
                    prompt.latestTokenUsage != 0 -> prompt.latestTokenUsage
                    else -> null
                }

                if (promptLength == null) return minimumContextLength
                if (promptLength > model.contextLength) {
                    logger.warn {
                        "Prompt length $promptLength was more than the maximum context length of model '${model.id}'," +
                            " falling back to the model's maximum context length ${model.contextLength}"
                    }
                    return model.contextLength
                }

                return (promptLength / granularity + 1) * granularity
            }
        }
    }
}
