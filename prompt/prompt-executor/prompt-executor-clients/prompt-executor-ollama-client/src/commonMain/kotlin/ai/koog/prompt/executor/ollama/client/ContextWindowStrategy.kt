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

    /**
     * Computes the context length for a given prompt and language model.
     * This may involve calculating the number of tokens used in the prompt
     * and determining if it fits within the model's context length constraints.
     *
     * @param prompt The [Prompt] containing the list of messages, unique identifier,
     *        and language model parameters that describe the input for the LLM.
     * @param model The [LLModel] representing the language model used to process the prompt,
     *        which includes its provider, identifier, capabilities, and context length.
     * @return The context length as a [Long], indicating the number of tokens used
     *         in the prompt, or `null` if it cannot be calculated.
     */
    public fun computeContextLength(prompt: Prompt, model: LLModel): Long?

    /**
     * Provides companion object-related strategies for determining the context window length.
     * It contains multiple strategies that are implemented as subtypes of [ContextWindowStrategy].
     */
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
            init {
                require(contextLength > 0) { "Context length must be positive but was: $contextLength" }
            }

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
         * @param contextChunkSize The granularity to use for computing the context window length. Defaults to 2048.
         * @param minimumChunkCount The minimum number of context chunks in the context.
         * @param maximumChunkCount The maximum number of context chunks in the context.
         *
         * Example: contextChunkSize = 512, minimumChunkCount = 2, maximumChunkCount = 4,
         *  then [minimumContextLength] = 1024 and [maximumContextLength] = 2048
         */
        public data class FitPrompt(
            val promptTokenizer: PromptTokenizer? = null,
            val contextChunkSize: Long = 2048,
            val minimumChunkCount: Long? = null,
            val maximumChunkCount: Long? = null
        ) : ContextWindowStrategy {

            private val minimumContextLength: Long? = minimumChunkCount?.let { cnt -> cnt * contextChunkSize }
            private val maximumContextLength: Long? = maximumChunkCount?.let { cnt -> cnt * contextChunkSize }

            init {
                require(contextChunkSize > 0) { "`contextChunkSize`` must be greater than 0" }
                require(minimumChunkCount == null || minimumChunkCount > 0) {
                    "`minimumChunkCount` must be a positive number or `null`"
                }

                if (minimumChunkCount != null && maximumChunkCount != null) {
                    require(minimumChunkCount <= maximumChunkCount) {
                        "`maximumChunkCount` ($maximumChunkCount) must be greater or equal" +
                            " to `minimumChunkCount` ($minimumChunkCount)"
                    }
                }
            }

            override fun computeContextLength(prompt: Prompt, model: LLModel): Long? {
                val promptLength = when {
                    promptTokenizer != null -> promptTokenizer.tokenCountFor(prompt)
                    prompt.latestTokenUsage != 0 -> prompt.latestTokenUsage
                    else -> null
                }

                if (promptLength == null) return minimumContextLength

                if (maximumContextLength != null && promptLength > maximumContextLength) {
                    logger.warn {
                        "Prompt length $promptLength was more than " +
                            "the maximum context length $maximumContextLength provideded"
                    }
                    return maximumContextLength
                }

                if (promptLength > model.contextLength) {
                    logger.warn {
                        "Prompt length $promptLength was more than the maximum context length of model '${model.id}'," +
                            " falling back to the model's maximum context length ${model.contextLength}"
                    }
                    return model.contextLength
                }

                return (promptLength / contextChunkSize + 1) * contextChunkSize
            }
        }
    }
}
