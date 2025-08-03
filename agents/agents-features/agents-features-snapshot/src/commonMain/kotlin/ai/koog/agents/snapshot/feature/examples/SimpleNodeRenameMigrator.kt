package ai.koog.agents.snapshot.feature.examples

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.CheckpointMigrator

/**
 * Example migrator that demonstrates how to rename node IDs in checkpoint data.
 * 
 * This migrator can handle migration from version 1 to version 2 for a specific
 * strategy, renaming "old_node_name" to "new_node_name".
 */
public class SimpleNodeRenameMigrator(
    private val strategyId: String,
    private val fromVersion: Int = 1,
    private val toVersion: Int = 2,
    private val nodeRenames: Map<String, String> = mapOf("old_node_name" to "new_node_name")
) : CheckpointMigrator {
    
    override fun canMigrate(strategyId: String?, from: Int, to: Int): Boolean {
        return strategyId == this.strategyId && 
               from == this.fromVersion && 
               to == this.toVersion
    }
    
    override suspend fun migrate(data: AgentCheckpointData, toVersion: Int): AgentCheckpointData {
        val newNodeId = nodeRenames[data.nodeId] ?: data.nodeId
        
        return data.copy(
            nodeId = newNodeId,
            graphVersion = toVersion,
            customMeta = data.customMeta + ("migrated_by" to "SimpleNodeRenameMigrator")
        )
    }
}

/**
 * Example migrator that demonstrates state transformation between versions.
 * 
 * This is a more complex example that could transform the lastInput data
 * structure when the input format changes between versions.
 */
public class StateTransformMigrator(
    private val strategyId: String,
    private val fromVersion: Int,
    private val toVersion: Int
) : CheckpointMigrator {
    
    override fun canMigrate(strategyId: String?, from: Int, to: Int): Boolean {
        return strategyId == this.strategyId &&
               from == this.fromVersion &&
               to == this.toVersion
    }
    
    override suspend fun migrate(data: AgentCheckpointData, toVersion: Int): AgentCheckpointData {
        // Example: Transform the lastInput JSON structure
        // In a real scenario, you would parse and transform the JSON data
        // based on your specific input format changes
        
        return data.copy(
            graphVersion = toVersion,
            customMeta = data.customMeta + mapOf(
                "migrated_by" to "StateTransformMigrator",
                "migration_timestamp" to System.currentTimeMillis().toString()
            )
        )
    }
}