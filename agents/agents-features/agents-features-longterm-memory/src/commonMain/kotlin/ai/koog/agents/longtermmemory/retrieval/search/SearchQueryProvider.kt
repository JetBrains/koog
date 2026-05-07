package ai.koog.agents.longtermmemory.retrieval.search

import ai.koog.prompt.dsl.Prompt

/**
 * Extracts a search query string from a [Prompt] to be used for memory retrieval.
 *
 * Implementations define how the search query is derived from the prompt messages.
 * For example, the default [LastUserMessageQueryProvider] uses the content of the last user message.
 *
 * @see LastUserMessageQueryProvider
 */
public fun interface SearchQueryProvider {
    /**
     * Extracts a search query string from the given [prompt].
     *
     * @param prompt the prompt to extract the query from.
     * @return the extracted query string, or `null` if no query could be extracted.
     */
    public fun provide(prompt: Prompt): String?
}
