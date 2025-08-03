package ai.koog.agents.snapshot.feature

/**
 * Interface for migrating agent checkpoints between different versions.
 * 
 * This interface allows implementing custom migration logic to handle changes in
 * strategy graphs, node IDs, or checkpoint data structure across versions.
 * 
 * Migration is performed automatically when loading checkpoints with older
 * [AgentCheckpointData.graphVersion] values.
 */
public interface CheckpointMigrator {
    /**
     * Determines if this migrator can handle migration from one version to another.
     * 
     * @param strategyId The strategy identifier from the checkpoint (may be null for legacy checkpoints)
     * @param from The current version of the checkpoint data
     * @param to The target version to migrate to
     * @return true if this migrator can perform the migration, false otherwise
     */
    public fun canMigrate(strategyId: String?, from: Int, to: Int): Boolean

    /**
     * Performs the migration of checkpoint data from its current version to the target version.
     * 
     * This method should update the checkpoint data structure to be compatible with
     * the target version, including:
     * - Updating node IDs if they have been renamed
     * - Transforming the lastInput data if its structure has changed  
     * - Updating the graphVersion to the target version
     * - Adding or modifying customMeta as needed
     * 
     * @param data The checkpoint data to migrate
     * @param toVersion The target version to migrate to
     * @return The migrated checkpoint data
     * @throws IllegalArgumentException if the migration cannot be performed
     */
    public suspend fun migrate(data: AgentCheckpointData, toVersion: Int): AgentCheckpointData
}