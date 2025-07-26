package ai.koog.agents.snapshot.prompts

import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Collection of prompts for agent snapshot feature.
 */
@InternalAgentsApi
public object SnapshotPrompts {
    

    /**
     * Prompt for LLM-based provider selection in AutoSelectForTask strategy.
     */
    public fun selectPersistencyProvider(
        taskDescription: String,
        providerDescriptions: String,
        availableProviderNames: String
    ): String = """
        Select the most appropriate persistence provider for the following task.

        Task: $taskDescription

        Available providers:
        $providerDescriptions

        Consider factors like:
        - Speed vs durability requirements for this task
        - Data criticality and retention needs
        - Expected frequency and access patterns
        - Cost and resource constraints

        You must select one of these exact provider names: $availableProviderNames

        Return the name of the most suitable provider.
    """.trimIndent()
}