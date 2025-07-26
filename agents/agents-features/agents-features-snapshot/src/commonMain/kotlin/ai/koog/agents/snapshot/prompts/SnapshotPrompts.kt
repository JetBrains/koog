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
        operationDescription: String,
        providerDescriptions: String,
        availableProviderNames: String
    ): String = """
        Select the most appropriate persistence provider for the following operation.

        Task context: $taskDescription

        Current operation: $operationDescription

        Available providers:
        $providerDescriptions

        Consider factors like:
        - Speed requirements (ephemeral vs durable)
        - Data criticality
        - Query needs
        - Cost implications

        You must select one of these exact provider names: $availableProviderNames

        Return the name of the most suitable provider.
    """.trimIndent()
}