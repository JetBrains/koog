package ai.koog.agents.snapshot.feature

import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AgentSnapshotBundleTest {

    @Test
    fun `AgentSnapshotBundle creates with default values`() {
        val checkpoint = createTestCheckpoint()
        val bundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint
        )
        
        assertEquals("test-agent", bundle.agentId)
        assertNotNull(bundle.bundleId)
        assertNotNull(bundle.createdAt)
        assertEquals(AgentSnapshotBundle.CURRENT_VERSION, bundle.version)
        assertEquals(checkpoint, bundle.checkpoint)
    }

    @Test
    fun `AgentSnapshotBundle serializes and deserializes correctly`() {
        val checkpoint = createTestCheckpoint()
        val metadata = buildJsonObject {
            put("testKey", "testValue")
            put("environment", "test")
        }
        
        val originalBundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint,
            metadata = metadata
        )
        
        // Serialize to JSON
        val json = originalBundle.toJson()
        assertNotNull(json)
        assertTrue(json.contains("test-agent"))
        
        // Deserialize from JSON
        val restoredBundle = AgentSnapshotBundle.fromJson(json)
        assertEquals(originalBundle.agentId, restoredBundle.agentId)
        assertEquals(originalBundle.bundleId, restoredBundle.bundleId)
        assertEquals(originalBundle.checkpoint.checkpointId, restoredBundle.checkpoint.checkpointId)
        assertEquals(originalBundle.metadata, restoredBundle.metadata)
    }

    @Test
    fun `AgentSnapshotBundle compresses and decompresses correctly`() {
        val checkpoint = createTestCheckpoint()
        val bundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint
        )
        
        // Compress to bytes
        val compressedBytes = bundle.toCompressedBytes()
        assertNotNull(compressedBytes)
        assertTrue(compressedBytes.isNotEmpty())
        
        // Decompress from bytes
        val restoredBundle = AgentSnapshotBundle.fromCompressedBytes(compressedBytes)
        assertEquals(bundle.agentId, restoredBundle.agentId)
        assertEquals(bundle.bundleId, restoredBundle.bundleId)
    }

    @Test
    fun `AgentSnapshotBundle validates compatibility correctly`() {
        val checkpoint = createTestCheckpoint()
        
        // Current version should be compatible
        val currentBundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint,
            version = AgentSnapshotBundle.CURRENT_VERSION
        )
        assertTrue(currentBundle.isCompatible())
        
        // Unknown version should be incompatible
        val futureBundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint,
            version = "999.0"
        )
        assertFalse(futureBundle.isCompatible())
    }

    @Test
    fun `AgentSnapshotBundle generates summary correctly`() {
        val checkpoint = createTestCheckpoint()
        val bundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint,
            memorySnapshot = buildJsonObject { put("facts", "test") },
            kvStoreSnapshot = buildJsonObject { put("data", "test") },
            metadata = buildJsonObject { put("env", "test") }
        )
        
        val summary = bundle.summary()
        assertTrue(summary.contains("test-agent"))
        assertTrue(summary.contains(checkpoint.checkpointId))
        assertTrue(summary.contains("present")) // Memory, KV, and metadata should be "present"
    }

    @Test
    fun `AgentSnapshotBundle withMetadata combines metadata correctly`() {
        val checkpoint = createTestCheckpoint()
        val originalMetadata = buildJsonObject {
            put("original", "value")
            put("shared", "original")
        }
        
        val bundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint,
            metadata = originalMetadata
        )
        
        val additionalMetadata = buildJsonObject {
            put("additional", "value")
            put("shared", "updated") // Should override original
        }
        
        val updatedBundle = bundle.withMetadata(additionalMetadata)
        
        // Original bundle should be unchanged
        assertEquals(originalMetadata, bundle.metadata)
        
        // Updated bundle should have combined metadata
        val combinedMetadata = updatedBundle.metadata!!
        assertEquals("value", combinedMetadata["original"]?.jsonPrimitive?.content)
        assertEquals("value", combinedMetadata["additional"]?.jsonPrimitive?.content)
        assertEquals("updated", combinedMetadata["shared"]?.jsonPrimitive?.content) // Should be overridden
    }

    @Test
    fun `AgentSnapshotBundle handles null optional fields correctly`() {
        val checkpoint = createTestCheckpoint()
        val bundle = AgentSnapshotBundle(
            agentId = "test-agent",
            checkpoint = checkpoint
            // All optional fields null
        )
        
        val summary = bundle.summary()
        assertTrue(summary.contains("none")) // Should show "none" for missing optional fields
        
        // Should still be compatible and serializable
        assertTrue(bundle.isCompatible())
        val json = bundle.toJson()
        val restored = AgentSnapshotBundle.fromJson(json)
        assertEquals(bundle.agentId, restored.agentId)
    }

    private fun createTestCheckpoint(): AgentCheckpointData {
        return AgentCheckpointData(
            checkpointId = Uuid.random().toString(),
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test-input"),
            messageHistory = emptyList()
        )
    }
}