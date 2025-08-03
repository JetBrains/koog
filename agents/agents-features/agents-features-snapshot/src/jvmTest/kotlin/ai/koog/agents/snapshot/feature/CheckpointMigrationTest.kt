package ai.koog.agents.snapshot.feature

import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckpointMigrationTest {

    @Test
    fun testNoMigrationNeeded() = runTest {
        val config = PersistencyFeatureConfig().apply {
            storage = InMemoryPersistencyStorageProvider("test-persistence-id")
            strategyId = "test-strategy"
            graphVersion = 2
        }
        
        val persistency = Persistency(config.storage, config)
        
        // Create a checkpoint with current version
        val checkpoint = AgentCheckpointData(
            checkpointId = "test-checkpoint",
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test"),
            messageHistory = emptyList(),
            strategyId = "test-strategy",
            graphVersion = 2
        )
        
        config.storage.saveCheckpoint(checkpoint)
        
        val restored = persistency.getLatestCheckpoint()
        assertNotNull(restored)
        assertEquals(2, restored.graphVersion)
        assertEquals("test-strategy", restored.strategyId)
    }

    @Test
    fun testSimpleMigration() = runTest {
        val config = PersistencyFeatureConfig().apply {
            storage = InMemoryPersistencyStorageProvider("test-persistence-id")
            strategyId = "test-strategy"
            graphVersion = 3
            migrators += TestMigrator()
        }
        
        val persistency = Persistency(config.storage, config)
        
        // Create an old checkpoint (version 2)
        val oldCheckpoint = AgentCheckpointData(
            checkpointId = "old-checkpoint",
            createdAt = Clock.System.now(),
            nodeId = "old_node_name",
            lastInput = JsonPrimitive("test"),
            messageHistory = listOf(
                Message.System("You are a helpful assistant", RequestMetaInfo.create(Clock.System))
            ),
            strategyId = "test-strategy",
            graphVersion = 2
        )
        
        config.storage.saveCheckpoint(oldCheckpoint)
        
        // Apply migrations directly
        val restored = persistency.applyMigrations(oldCheckpoint)
        assertNotNull(restored)
        assertEquals(3, restored.graphVersion)
        assertEquals("new_node_name", restored.nodeId) // Should be migrated
        assertEquals("test-strategy", restored.strategyId)
    }

    @Test
    fun testFutureVersionRejection() = runTest {
        val config = PersistencyFeatureConfig().apply {
            storage = InMemoryPersistencyStorageProvider("test-persistence-id")
            strategyId = "test-strategy"
            graphVersion = 2 // Current version is 2
        }
        
        val persistency = Persistency(config.storage, config)
        
        // Create a checkpoint from the future (version 3)
        val futureCheckpoint = AgentCheckpointData(
            checkpointId = "future-checkpoint",
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test"),
            messageHistory = emptyList(),
            strategyId = "test-strategy",
            graphVersion = 3 // Higher than current version
        )
        
        config.storage.saveCheckpoint(futureCheckpoint)
        
        // Should throw exception when trying to apply migration to future checkpoint
        assertFailsWith<IllegalStateException> {
            persistency.applyMigrations(futureCheckpoint)
        }
    }

    @Test
    fun testHistoryPolicyApplication() = runTest {
        val config = PersistencyFeatureConfig().apply {
            storage = InMemoryPersistencyStorageProvider("test-persistence-id")
            strategyId = "test-strategy"
            graphVersion = 1
            historyPolicy = MessageCountHistoryPolicy(maxMessages = 2)
        }
        
        val persistency = Persistency(config.storage, config)
        
        // Create a checkpoint with more messages than the policy allows
        val messages = listOf(
            Message.System("You are helpful", RequestMetaInfo.create(Clock.System)),
            Message.User("Hello", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Hi there!", ResponseMetaInfo.create(Clock.System)),
            Message.User("How are you?", RequestMetaInfo.create(Clock.System))
        )
        
        val checkpoint = AgentCheckpointData(
            checkpointId = "test-checkpoint",
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test"),
            messageHistory = messages,
            strategyId = "test-strategy",
            graphVersion = 1
        )
        
        // History should be trimmed during checkpoint creation
        // Note: This test would need to be updated to test actual checkpoint creation through the feature
        assertEquals(4, checkpoint.messageHistory.size) // Before trimming
        
        // Verify policy works correctly
        val trimmed = config.historyPolicy!!.trim(messages)
        assertEquals(2, trimmed.size)
        assertEquals("Hi there!", (trimmed[0] as Message.Assistant).content)
        assertEquals("How are you?", (trimmed[1] as Message.User).content)
    }

    @Test
    fun testHashValidationWarning() = runTest {
        val config = PersistencyFeatureConfig().apply {
            storage = InMemoryPersistencyStorageProvider("test-persistence-id")
            strategyId = "test-strategy"
            graphVersion = 2
            graphHash = "current-hash"
        }
        
        val persistency = Persistency(config.storage, config)
        
        // Create a checkpoint with a different hash
        val checkpoint = AgentCheckpointData(
            checkpointId = "test-checkpoint",
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test"),
            messageHistory = emptyList(),
            strategyId = "test-strategy",
            graphVersion = 2,
            graphHash = "different-hash"
        )
        
        config.storage.saveCheckpoint(checkpoint)
        
        // Should not throw, but would emit warning (we can't easily test logging here)
        val restored = persistency.applyMigrations(checkpoint)
        assertNotNull(restored)
        assertEquals("different-hash", restored.graphHash)
    }

    @Test
    fun testLegacyCheckpointWithNullFields() = runTest {
        val config = PersistencyFeatureConfig().apply {
            storage = InMemoryPersistencyStorageProvider("test-persistence-id")
            strategyId = "test-strategy"
            graphVersion = 2
            migrators += TestMigrator()
        }
        
        val persistency = Persistency(config.storage, config)
        
        // Create a legacy checkpoint (all new fields null/default)
        val legacyCheckpoint = AgentCheckpointData(
            checkpointId = "legacy-checkpoint",
            createdAt = Clock.System.now(),
            nodeId = "old_node_name",
            lastInput = JsonPrimitive("test"),
            messageHistory = emptyList()
            // strategyId = null, graphVersion = 1, graphHash = null, customMeta = emptyMap()
        )
        
        config.storage.saveCheckpoint(legacyCheckpoint)
        
        // Should handle legacy checkpoint correctly
        val restored = persistency.applyMigrations(legacyCheckpoint)
        assertNotNull(restored)
        assertEquals(2, restored.graphVersion) // Migrated to current version
        assertEquals("new_node_name", restored.nodeId) // Node name migrated
        assertNull(restored.strategyId) // Legacy checkpoint had no strategy ID
    }
}

/**
 * Test migrator that renames "old_node_name" to "new_node_name"
 */
class TestMigrator : CheckpointMigrator {
    override fun canMigrate(strategyId: String?, from: Int, to: Int): Boolean {
        return from < to && to <= 3
    }

    override suspend fun migrate(data: AgentCheckpointData, toVersion: Int): AgentCheckpointData {
        val newNodeId = when (data.nodeId) {
            "old_node_name" -> "new_node_name"
            else -> data.nodeId
        }
        
        return data.copy(
            nodeId = newNodeId,
            graphVersion = toVersion
        )
    }
}