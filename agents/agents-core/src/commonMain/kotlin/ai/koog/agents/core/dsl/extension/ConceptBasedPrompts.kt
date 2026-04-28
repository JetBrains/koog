package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Collection of prompt for agent memory feature.
 */
@InternalAgentsApi
public object ConceptBasedPrompts {
    /**
     * Tag to wrap history.
     */
    @Suppress("ConstPropertyName")
    public const val historyWrapperTag: String = "conversation_to_extract_facts"

    /**
     * Single fact prompt.
     */
    public fun singleFactPrompt(concept: Concept): String =
        """You are a specialized information extractor for compressing agent conversation histories.

        You will receive a conversation history enclosed in <$historyWrapperTag> tags. Your task is to extract THE SINGLE MOST IMPORTANT fact about "${concept.keyword}" (${concept.description}).
        
        Critical extraction rules:
        1. Focus on THE MOST ESSENTIAL OUTCOME or ESTABLISHED INFORMATION
        2. When you see tool results/observations, extract only the most crucial discovered fact
        3. The fact must be self-contained - assume it will be the only available context later
        4. Choose the fact with the broadest impact on understanding this concept
        
        Output constraints:
        - Exactly one fact
        - Must be a complete, self-contained statement
        
        Respond with a JSON object containing a single "fact" field.
        """.trimIndent()

    /**
     * Multiple facts prompt.
     */
    public fun multipleFactsPrompt(concept: Concept): String =
        """You are a specialized information extractor for compressing agent conversation histories.
        
        You will receive a conversation history enclosed in <$historyWrapperTag> tags. Your task is to extract ONLY the essential facts about "${concept.keyword}" (${concept.description}).
        
        Critical extraction rules:
        1. Focus on OUTCOMES and ESTABLISHED INFORMATION, not actions taken
        2. When you see tool results/observations, extract only the discovered facts, not the process
        3. Each fact must be self-contained - assume it will be the only available context later
        4. Combine related information into single, comprehensive facts when possible
        
        Output constraints:
        - Facts must be complete statements that stand alone
        - Skip any fact that just describes what was attempted or checked
        
        Respond with a JSON object containing a "facts" array, where each element has a "fact" field.
        """.trimIndent()
}
