package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.agents.longtermmemory.retrieval.SearchResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo

/**
 * A [PromptAugmenter] that inserts retrieved context as a user message before the last user message.
 *
 * If the prompt contains no user messages, the original prompt is returned unchanged.
 *
 * @param template The template for user context insertion. Use [PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER] placeholder.
 * @param contextPrefix The prefix to add before relevant context.
 * @see PromptAugmenter
 */
public class UserPromptAugmenter(
    private val template: String = DEFAULT_USER_PROMPT_TEMPLATE,
    private val contextPrefix: String = PromptAugmenter.DEFAULT_CONTEXT_PREFIX
) : PromptAugmenter {

    /**
     * Companion object with default templates.
     */
    public companion object {
        /**
         * Default template for user context insertion.
         * Use [PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER] placeholder.
         */
        public val DEFAULT_USER_PROMPT_TEMPLATE: String =
            """
            |Here is some relevant context:
            |
            |${PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER}
            |
            |Based on the above context, please answer the following question:
            """.trimMargin().trim()
    }

    /**
     * Builder for [UserPromptAugmenter].
     *
     * Provides a fluent API for constructing a [UserPromptAugmenter],
     * which is convenient for Java users.
     *
     * Example usage (Java):
     * ```java
     * new UserPromptAugmenter.Builder()
     *     .withTemplate("Context: {relevant_context}")
     *     .build()
     * ```
     */
    public class Builder {
        /** The template for user context insertion. */
        public var template: String = DEFAULT_USER_PROMPT_TEMPLATE

        /** The prefix to add before relevant context. */
        public var contextPrefix: String = PromptAugmenter.DEFAULT_CONTEXT_PREFIX

        /** Fluent setter for [template]. */
        public fun withTemplate(template: String): Builder =
            apply { this.template = template }

        /** Fluent setter for [contextPrefix]. */
        public fun withContextPrefix(contextPrefix: String): Builder =
            apply { this.contextPrefix = contextPrefix }

        /** Builds a [UserPromptAugmenter] from the current settings. */
        public fun build(): UserPromptAugmenter = UserPromptAugmenter(template, contextPrefix)
    }

    override fun augment(originalPrompt: Prompt, relevantContext: List<SearchResult>): Prompt {
        if (relevantContext.isEmpty()) return originalPrompt
        if (originalPrompt.messages.none { it is Message.User }) return originalPrompt

        val relevantContextText = PromptAugmenter.formatContext(relevantContext, contextPrefix)
        val formattedContext = PromptAugmenter.formatTemplate(template, relevantContextText)
        if (formattedContext.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val lastUserIndex = messages.indexOfLast { it is Message.User }
            if (lastUserIndex >= 0) {
                messages.toMutableList().apply {
                    add(lastUserIndex, Message.User(formattedContext, RequestMetaInfo.Empty))
                }
            } else {
                messages
            }
        }
    }
}
