package ai.koog.agents.longtermmemory.retrieval

import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.prompt.dsl.Prompt

/**
 * Extracts a search query string from a [Prompt] to be used for memory retrieval.
 *
 * Implementations define how the search query is derived from the prompt messages.
 * For example, the default [LastUserMessageQueryExtractor] uses the content of the last user message.
 *
 * @see LastUserMessageQueryExtractor
 */
@ExperimentalAgentsApi
public fun interface QueryExtractor {
    /**
     * Extracts a search query string from the given [prompt].
     *
     * @param prompt the prompt to extract the query from.
     * @return the extracted query string, or `null` if no query could be extracted.
     */
    public fun extract(prompt: Prompt): String?
}
