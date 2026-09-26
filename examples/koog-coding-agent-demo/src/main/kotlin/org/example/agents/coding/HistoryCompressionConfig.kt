package org.example.agents.coding

import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType

val CODE_AGENT_COMPRESSION_STRATEGY = RetrieveFactsFromHistory(
    Concept(
        "project-structure",
        "What is the structure of this project?",
        FactType.MULTIPLE
    ),
    Concept(
        "project-dependencies",
        "What are the dependencies of this project?",
        FactType.MULTIPLE
    ),
    Concept(
        "important-achievements",
        "What has been achieved during the execution of this current agent?",
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