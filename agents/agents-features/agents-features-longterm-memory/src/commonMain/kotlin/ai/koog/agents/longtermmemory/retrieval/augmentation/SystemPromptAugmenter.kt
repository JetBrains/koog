package ai.koog.agents.longtermmemory.retrieval.augmentation

import ai.koog.agents.longtermmemory.retrieval.SearchResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo

/**
 * A [PromptAugmenter] that inserts retrieved context as a system message at the beginning of the prompt.
 *
 * If an existing system message is present, the new context system message is inserted
 * before it, keeping each system message focused on a single concern.
 * If there is no system message in the prompt, the prompt is returned unchanged.
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

    override fun augment(originalPrompt: Prompt, relevantContext: List<SearchResult>): Prompt {
        if (relevantContext.isEmpty()) return originalPrompt
        if (originalPrompt.messages.none { it is Message.System }) return originalPrompt

        val relevantContextText = PromptAugmenter.formatContext(relevantContext, contextPrefix)
        val contextMessage = PromptAugmenter.formatTemplate(template, relevantContextText)
        if (contextMessage.isBlank()) return originalPrompt

        return originalPrompt.withMessages { messages ->
            val systemMessage = Message.System(contextMessage, RequestMetaInfo.Empty)
            listOf<Message>(systemMessage) + messages
        }
    }
}
