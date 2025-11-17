package ai.koog.agents.example.codeagent.step05

import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.prompt.dsl.Prompt

/**
 * Triggers compression when history exceeds 200 messages OR 200k characters (~50k tokens).
 */
val CODE_AGENT_HISTORY_TOO_BIG: (Prompt) -> Boolean = { prompt ->
    prompt.messages.size > 200 || prompt.messages.sumOf { it.content.length } > 200_000
}

/**
 * Extracts key facts from conversation history.
 * LLM answers these questions, and the answers become the compressed history.
 */
val CODE_AGENT_COMPRESSION = RetrieveFactsFromHistory(
    Concept(
        "project-structure",
        "What is the project structure of this project?",
        FactType.MULTIPLE
    ),
    Concept(
        "project-dependencies",
        "What are the project dependencies of this project?",
        FactType.MULTIPLE
    ),
    Concept(
        "important-achievements",
        "What has been achieved during the execution of this current agent",
        FactType.MULTIPLE
    ),
    Concept(
        "agent-goal",
        "What is the primary goal or task the agent is trying to accomplish in this session?",
        FactType.SINGLE
    ),
    Concept(
        "tool-interaction-summary",
        "Summarize the sequence of tools used, the reason for using each tool, and the key results or outcomes obtained.",
        FactType.MULTIPLE
    ),
    Concept(
        "key-findings-and-data",
        "What are the most critical pieces of information, data points, code snippets, or insights discovered or generated during the process, beyond project structure or dependencies?",
        FactType.MULTIPLE
    ),
    Concept(
        "current-status-and-conclusions",
        "Describe the current progress status towards the overall goal and summarize any intermediate conclusions reached so far.",
        FactType.SINGLE
    ),
    Concept(
        "pending-tasks-and-issues",
        "What are the immediate next steps planned or required? Are there any unresolved questions, issues, decisions to be made, or blockers encountered?",
        FactType.MULTIPLE
    )
)
