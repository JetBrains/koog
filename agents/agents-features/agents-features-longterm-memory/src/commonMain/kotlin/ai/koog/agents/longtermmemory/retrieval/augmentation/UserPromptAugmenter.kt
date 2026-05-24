package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.SearchResult

/**
 * A [PromptAugmenter] that injects retrieved context into the last [Message.User] of the prompt.
 *
 * The retrieved context is rendered through [template] and added as an additional
 * [MessagePart.Text] **appended** to the existing parts of the last user message. The original
 * user message is replaced with a copy that:
 * - preserves its [Message.metaInfo] and [Message.id],
 * - preserves all existing parts (text, attachments, tool results, etc.),
 * - gains one extra [MessagePart.Text] at the end carrying the formatted context.
 *
 * If the prompt contains no [Message.User], or the formatted context is blank, or the relevant
 * context list is empty, the original prompt is returned unchanged.
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
            |Here is some relevant context to help answer the question above:
            |
            |${PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER}
            |
            |Please answer the question above based on this context.
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

    override fun augment(originalPrompt: Prompt, relevantContext: List<SearchResult<TextDocument>>): Prompt {
        if (relevantContext.isEmpty()) return originalPrompt
        if (originalPrompt.messages.none { it is Message.User }) return originalPrompt

        val relevantContextText = PromptAugmenter.formatContext(relevantContext, contextPrefix)
        val formattedContext = PromptAugmenter.formatTemplate(template, relevantContextText)
        if (formattedContext.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val lastUserIndex = messages.indexOfLast { it is Message.User }
            if (lastUserIndex < 0) return@withMessages messages

            val original = messages[lastUserIndex] as Message.User
            val updated = original.copy(
                parts = original.parts + MessagePart.Text(formattedContext)
            )
            messages.toMutableList().also { it[lastUserIndex] = updated }
        }
    }
}
