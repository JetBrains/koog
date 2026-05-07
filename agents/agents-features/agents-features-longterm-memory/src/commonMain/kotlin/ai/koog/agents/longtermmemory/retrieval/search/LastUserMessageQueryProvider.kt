package ai.koog.agents.longtermmemory.retrieval.search

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

/**
 * Default [SearchQueryProvider] implementation that extracts the content of the last user message from the prompt.
 */
public class LastUserMessageQueryProvider : SearchQueryProvider {
    override fun provide(prompt: Prompt): String? {
        return prompt.messages.lastOrNull { it.role == Message.Role.User }?.content
    }
}
