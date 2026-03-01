package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.agents.longtermmemory.retrieval.SearchResult
import ai.koog.prompt.dsl.Prompt

/**
 * Interface for augmenting prompts with relevant context retrieved from memory.
 *
 * Implementations define how retrieved context is inserted into the prompt.
 * Two built-in implementations are provided:
 * - [SystemPromptAugmenter] — inserts context as a system message
 * - [UserPromptAugmenter] — inserts context as a user message
 *
 * Can also be implemented as a lambda thanks to the `fun interface` declaration:
 * ```kotlin
 * val custom = PromptAugmenter { prompt, context ->
 *     // custom augmentation logic
 *     prompt
 * }
 * ```
 *
 * @see SystemPromptAugmenter
 * @see UserPromptAugmenter
 * @see ai.koog.agents.longtermmemory.feature.LongTermMemory
 */
public fun interface PromptAugmenter {

    /**
     * Augments the given prompt with relevant context.
     *
     * @param originalPrompt The original prompt to augment.
     * @param relevantContext The list of search results containing relevant context.
     * @return A new [Prompt] with the context inserted, or the original prompt if no augmentation is needed.
     */
    public fun augment(originalPrompt: Prompt, relevantContext: List<SearchResult>): Prompt

    /**
     * Companion object with constants, static methods, and builder entry point.
     */
    public companion object {
        /**
         * Returns a builder that lets you choose a default [PromptAugmenter] implementation.
         *
         * Example usage (Java):
         * ```java
         * PromptAugmenter.builder()
         *     .system()
         *     .withTemplate("Use this context: {relevant_context}")
         *     .build()
         * ```
         */
        @kotlin.jvm.JvmStatic
        public fun builder(): PromptAugmenterBuilder = PromptAugmenterBuilder()

        /**
         * Placeholder in prompt templates for insertion of relevant context.
         */
        public const val RELEVANT_CONTEXT_PLACEHOLDER: String = "{relevant_context}"

        /**
         * The prefix to add before relevant context.
         */
        public const val DEFAULT_CONTEXT_PREFIX: String = "Relevant information:\n"

        /**
         * Formats a list of search results into a numbered text block with the given prefix.
         */
        public fun formatContext(
            relevantContext: List<SearchResult>,
            contextPrefix: String = DEFAULT_CONTEXT_PREFIX
        ): String {
            return contextPrefix + relevantContext.mapIndexed { index, result ->
                "[${index + 1}] ${result.record.content}"
            }.joinToString("\n\n")
        }

        /**
         * Formats the template by replacing placeholders with actual content.
         */
        public fun formatTemplate(template: String, relevantContextText: String): String {
            return template
                .replace(RELEVANT_CONTEXT_PLACEHOLDER, relevantContextText)
                .trim()
        }
    }
}

/**
 * Intermediate builder that lets callers select a [PromptAugmenter] implementation.
 */
public class PromptAugmenterBuilder {
    /**
     * Select the [SystemPromptAugmenter] implementation.
     * Returns its [SystemPromptAugmenter.Builder] for further configuration.
     */
    public fun system(): SystemPromptAugmenter.Builder = SystemPromptAugmenter.Builder()

    /**
     * Select the [UserPromptAugmenter] implementation.
     * Returns its [UserPromptAugmenter.Builder] for further configuration.
     */
    public fun user(): UserPromptAugmenter.Builder = UserPromptAugmenter.Builder()
}
