package ai.koog.agents.snapshot.providers

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.snapshot.feature.AgentCheckpointData

/**
 * Storage provider interface for agent checkpoint persistence.
 * 
 * This interface provides context-aware checkpoint storage operations,
 * enabling efficient multi-user and multi-session checkpoint management.
 * The AIAgentContext parameter allows providers to:
 * - Filter checkpoints by agent ID, user ID, or session
 * - Perform efficient database queries instead of in-memory filtering
 * - Access agent state for storage decisions
 * 
 * **Breaking Change Note**: This interface now requires AIAgentContext parameters
 * for all methods. This enables proper multi-user support and fixes performance
 * issues where all checkpoints were loaded into memory for filtering.
 */
public interface PersistencyStorageProvider {
    /**
     * Retrieves all checkpoints for the agent context.
     * 
     * Providers should filter checkpoints based on the agent context
     * (e.g., by agent ID, user ID, session ID) rather than returning
     * all checkpoints in the system.
     * 
     * @param context The agent context containing filtering information
     * @return List of checkpoints relevant to the agent context
     */
    public suspend fun getCheckpoints(context: AIAgentContextBase): List<AgentCheckpointData>
    
    /**
     * Saves a checkpoint for the agent context.
     * 
     * @param agentCheckpointData The checkpoint data to save
     * @param context The agent context for associating the checkpoint
     */
    public suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData, context: AIAgentContextBase)
    
    /**
     * Retrieves the latest checkpoint for the agent context.
     * 
     * @param context The agent context to find the latest checkpoint for
     * @return The most recent checkpoint, or null if none exists
     */
    public suspend fun getLatestCheckpoint(context: AIAgentContextBase): AgentCheckpointData?
    
    /**
     * Retrieves a specific checkpoint by ID within the agent context.
     * 
     * This method should filter by both checkpoint ID and agent context,
     * ensuring users can only access their own checkpoints.
     * 
     * @param checkpointId The ID of the checkpoint to retrieve
     * @param context The agent context for security/filtering
     * @return The checkpoint if found and accessible, null otherwise
     */
    public suspend fun getCheckpointById(checkpointId: String, context: AIAgentContextBase): AgentCheckpointData?
}
