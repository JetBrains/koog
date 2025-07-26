package ai.koog.agents.snapshot.prompts

import ai.koog.agents.core.annotation.InternalAgentsApi

/**
 * Collection of prompts for agent snapshot feature.
 */
@InternalAgentsApi
public object SnapshotPrompts {
    
    /**
     * Prompt for LLM-based checkpoint storage type analysis in SmartHybrid strategy.
     */
    public fun analyzePersistencyCheckpoint(
        taskDescription: String,
        nodeId: String,
        messageHistoryLength: Int,
        createdAt: String,
        hasCriticalProvider: Boolean
    ): String = """
        Analyze this agent checkpoint and determine the most appropriate storage type.
        
        Task Context: $taskDescription
        
        Checkpoint Details:
        - Node ID: $nodeId
        - Message History Length: $messageHistoryLength
        - Created At: $createdAt
        
        Available Storage Types:
        - "ephemeral": Fast, temporary storage for mid-execution checkpoints that don't need long-term persistence
        - "durable": Reliable, long-term storage for important checkpoints and session state
        - "critical": Most reliable storage for final results or critical decision points${if (!hasCriticalProvider) " (not available)" else ""}
        
        Consider:
        - Is this a mid-execution checkpoint or an important milestone/result?
        - Does the node ID suggest temporary processing or final state?
        - Does the message history indicate significant progress worth preserving?
        
        Choose the most appropriate storage type: ${
            if (hasCriticalProvider) "ephemeral, durable, critical" else "ephemeral, durable"
        }
    """.trimIndent()

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