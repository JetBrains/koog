package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message

/**
 * Default [QueryExtractor] implementation that extracts the content of the last user message from the prompt.
 */
@ExperimentalAgentsApi
public class LastUserMessageQueryExtractor : QueryExtractor {
    override fun extract(prompt: Prompt): String? {
        return prompt.messages.lastOrNull { it.role == Message.Role.User }?.content
    }
}
