package ai.koog.agents.memory.prompts

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.MemorySubject

/**
 * Collection of prompt for agent memory feature.
 */
@InternalAgentsApi
public object MemoryPrompts {
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
        2. When you see tool calls/observations, extract only the most crucial discovered fact
        3. The fact must be self-contained - assume it will be the only available context later
        4. Choose the fact with the broadest impact on understanding this concept
        
        Output constraints:
        - Exactly one fact
        - No explanations, formatting, or preamble
        - Must be a complete, self-contained statement
        
        Output only the fact.
        """.trimIndent()

    /**
     * Multiple facts prompt.
     */
    public fun multipleFactsPrompt(concept: Concept): String =
        """You are a specialized information extractor for compressing agent conversation histories.
        
        You will receive a conversation history enclosed in <$historyWrapperTag> tags. Your task is to extract ONLY the essential facts about "${concept.keyword}" (${concept.description}).
        
        Critical extraction rules:
        1. Focus on OUTCOMES and ESTABLISHED INFORMATION, not actions taken
        2. When you see tool calls/observations, extract only the discovered facts, not the process
        3. Each fact must be self-contained - assume it will be the only available context later
        4. Combine related information into single, comprehensive facts when possible
        
        Output constraints:
        - One fact per line
        - No explanations, headers, numbering, or formatting
        - Facts must be complete statements that stand alone
        - Skip any fact that just describes what was attempted or checked
        
        Output only the facts, nothing else.
        """.trimIndent()

    /**
     * Auto detect facts prompt.
     */
    public fun autoDetectFacts(subjects: List<MemorySubject>): String = """
        Analyze the conversation history and identify important facts about:
        ${
        subjects.joinToString("\n") { subject ->
            "        - [subject: \"${subject.name}\"] ${subject.promptDescription}"
        }
    }

        For each fact:
        1. Provide a relevant subject (USE SAME SUBJECTS AS DESCRIBED ABOVE!)
        2. Provide a keyword (e.g., 'user-preference', 'project-requirement')
        3. Write a description that helps identify similar information
        4. Provide the actual fact value

        Format your response as a JSON objects:
        [
            {
                "subject": "string",
                "keyword": "string",
                "description": "string",
                "value": "string"
            }
        ]
    """.trimIndent()
}