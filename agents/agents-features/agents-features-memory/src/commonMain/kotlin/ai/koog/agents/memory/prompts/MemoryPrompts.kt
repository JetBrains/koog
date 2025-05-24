package ai.koog.agents.memory.prompts

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.MemorySubject

internal object MemoryPrompts {
    internal fun singleFactPrompt(concept: Concept) =
        "Based on our previous conversation, what is the most important fact about concept \"${concept.keyword}\" (${concept.description}) ? " +
                "Provide a single, concise fact or answer.\n" +
                "ONLY reply with the fact. Don't write any explanations or any polite answers to my question!"

    internal fun multipleFactsPrompt(concept: Concept) =
        "Based on our previous conversation, what are the key facts about concept \"${concept.keyword}\" (${concept.description}) ? " +
                "List each fact separately on new lines.\n" +
                "Each fact should fit into a single line!\n" +
                "ONLY reply with the list of facts on separate lines. Don't write any explanations or any polite answers to my question!"

    internal fun autoDetectFacts(subjects: List<MemorySubject>): String = """
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