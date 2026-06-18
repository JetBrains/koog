package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.search.SearchResult

/**
 * A [PromptAugmenter] that injects retrieved context into the first [Message.System] of the prompt.
 *
 * The retrieved context is rendered through [template] and added as an additional
 * [MessagePart.Text] **appended** to the existing parts of the first system message.
 * The original system message is replaced with a copy that:
 * - preserves its [Message.metaInfo] and [Message.id],
 * - preserves all existing parts (text, attachments, etc.),
 * - gains an extra [MessagePart.Text] entry at the end with formatted context.
 *
 * If the prompt contains no [Message.System], or the formatted context is blank, or the relevant
 * context list is empty, the original prompt is returned unchanged.
 *
 * @param template The template for the system message. Use [PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER] placeholder.
 * @param contextPrefix The prefix to add before relevant context.
 * @see PromptAugmenter
 */
public class SystemPromptAugmenter(
    private val template: String = DEFAULT_SYSTEM_PROMPT_TEMPLATE,
    private val contextPrefix: String = PromptAugmenter.DEFAULT_CONTEXT_PREFIX
) : PromptAugmenter {

    /**
     * Companion object with default templates.
     */
    public companion object {
        /**
         * Default template for the system message.
         * Use [PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER] placeholder.
         */
        public val DEFAULT_SYSTEM_PROMPT_TEMPLATE: String =
            """
            |Use the following information to answer the user's question.
            |
            |${PromptAugmenter.RELEVANT_CONTEXT_PLACEHOLDER}
            |
            |Answer the user's question based on the above context. If the context doesn't contain relevant information, say so.
            """.trimMargin().trim()
    }

    /**
     * Builder for [SystemPromptAugmenter].
     *
     * Provides a fluent API for constructing a [SystemPromptAugmenter],
     * which is convenient for Java users.
     *
     * Example usage (Java):
     * ```java
     * new SystemPromptAugmenter.Builder()
     *     .withTemplate("Use this context: {relevant_context}")
     *     .build()
     * ```
     */
    public class Builder {
        /** The template for the system message. */
        public var template: String = DEFAULT_SYSTEM_PROMPT_TEMPLATE

        /** The prefix to add before relevant context. */
        public var contextPrefix: String = PromptAugmenter.DEFAULT_CONTEXT_PREFIX

        /** Fluent setter for [template]. */
        public fun withTemplate(template: String): Builder =
            apply { this.template = template }

        /** Fluent setter for [contextPrefix]. */
        public fun withContextPrefix(contextPrefix: String): Builder =
            apply { this.contextPrefix = contextPrefix }

        /** Builds a [SystemPromptAugmenter] from the current settings. */
        public fun build(): SystemPromptAugmenter = SystemPromptAugmenter(template, contextPrefix)
    }

    override fun augment(originalPrompt: Prompt, relevantContext: List<SearchResult<TextDocument>>): Prompt {
        if (relevantContext.isEmpty()) return originalPrompt
        if (originalPrompt.messages.none { it is Message.System }) return originalPrompt

        val relevantContextText = PromptAugmenter.formatContext(relevantContext, contextPrefix)
        val formattedContext = PromptAugmenter.formatTemplate(template, relevantContextText)
        if (formattedContext.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val systemIndex = messages.indexOfFirst { it is Message.System }
            if (systemIndex < 0) return@withMessages messages

            val original = messages[systemIndex] as Message.System
            val updated = original.copy(
                parts = original.parts + MessagePart.Text(formattedContext)
            )
            messages.toMutableList().also { it[systemIndex] = updated }
        }
    }
}
