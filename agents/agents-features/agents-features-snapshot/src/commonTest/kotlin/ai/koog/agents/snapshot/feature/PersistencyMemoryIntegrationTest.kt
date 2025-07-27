package ai.koog.agents.snapshot.feature

import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class PersistencyMemoryIntegrationTest {

    @Test
    fun `Persistency captures memory snapshots when configured`() = runTest {
        val mockMemoryProvider = MockMemoryProvider()
        val mockStorageProvider = MockStorageProvider()
        val config = PersistencyFeatureConfig().apply {
            storage = mockStorageProvider
            includeMemorySnapshot = true
        }
        
        val persistency = Persistency(mockStorageProvider, config)
        val mockContext = MockAgentContext("test-agent", mockMemoryProvider)
        
        // Add some facts to memory
        val concept = Concept("test-concept", "Test concept", FactType.SINGLE)
        val fact = SingleFact(concept, Clock.System.now().toEpochMilliseconds(), "test value")
        mockMemoryProvider.save(fact, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        // Create checkpoint
        val checkpoint = persistency.createCheckpoint(
            agentContext = mockContext,
            nodeId = "test-node",
            lastInput = "test-input",
            lastInputType = typeOf<String>()
        )
        
        assertNotNull(checkpoint, "Checkpoint should be created")
        assertNotNull(checkpoint.memorySnapshot, "Memory snapshot should be captured")
        assertTrue(mockStorageProvider.savedCheckpoints.contains(checkpoint))
    }

    @Test
    fun `Persistency does not capture memory snapshots when disabled`() = runTest {
        val mockMemoryProvider = MockMemoryProvider()
        val mockStorageProvider = MockStorageProvider()
        val config = PersistencyFeatureConfig().apply {
            storage = mockStorageProvider
            includeMemorySnapshot = false // Disabled
        }
        
        val persistency = Persistency(mockStorageProvider, config)
        val mockContext = MockAgentContext("test-agent", mockMemoryProvider)
        
        // Add some facts to memory
        val concept = Concept("test-concept", "Test concept", FactType.SINGLE)
        val fact = SingleFact(concept, Clock.System.now().toEpochMilliseconds(), "test value")
        mockMemoryProvider.save(fact, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        // Create checkpoint
        val checkpoint = persistency.createCheckpoint(
            agentContext = mockContext,
            nodeId = "test-node", 
            lastInput = "test-input",
            lastInputType = typeOf<String>()
        )
        
        assertNotNull(checkpoint, "Checkpoint should be created")
        assertNull(checkpoint.memorySnapshot, "Memory snapshot should NOT be captured")
    }

    @Test
    fun `Persistency captures custom data when configured`() = runTest {
        val mockMemoryProvider = MockMemoryProvider()
        val mockStorageProvider = MockStorageProvider()
        val config = PersistencyFeatureConfig().apply {
            storage = mockStorageProvider
            extraSnapshotDataProvider = {
                buildJsonObject {
                    put("customData", "test value")
                    put("agentId", id)
                }
            }
        }
        
        val persistency = Persistency(mockStorageProvider, config)
        val mockContext = MockAgentContext("test-agent", mockMemoryProvider)
        
        // Create checkpoint
        val checkpoint = persistency.createCheckpoint(
            agentContext = mockContext,
            nodeId = "test-node",
            lastInput = "test-input", 
            lastInputType = typeOf<String>()
        )
        
        assertNotNull(checkpoint, "Checkpoint should be created")
        assertNotNull(checkpoint.extraSnapshotData, "Extra snapshot data should be captured")
        assertEquals("test value", checkpoint.extraSnapshotData["customData"]?.jsonPrimitive?.content)
        assertEquals("test-agent", checkpoint.extraSnapshotData["agentId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `Persistency rollback restores memory snapshots`() = runTest {
        val captureMemoryProvider = MockMemoryProvider()
        val restoreMemoryProvider = MockMemoryProvider()
        val mockStorageProvider = MockStorageProvider()
        val config = PersistencyFeatureConfig().apply {
            storage = mockStorageProvider
            includeMemorySnapshot = true
        }
        
        val persistency = Persistency(mockStorageProvider, config)
        val captureContext = MockAgentContext("test-agent", captureMemoryProvider)
        val restoreContext = MockAgentContext("test-agent", restoreMemoryProvider)
        
        // Add facts to capture context
        val concept = Concept("restore-test", "Restore test concept", FactType.SINGLE)
        val fact = SingleFact(concept, Clock.System.now().toEpochMilliseconds(), "original value")
        captureMemoryProvider.save(fact, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        // Create checkpoint
        val checkpoint = persistency.createCheckpoint(
            agentContext = captureContext,
            nodeId = "test-node",
            lastInput = "test-input",
            lastInputType = typeOf<String>()
        )
        assertNotNull(checkpoint)
        
        // Rollback in different context
        val extraData = persistency.rollbackToCheckpoint<String>(checkpoint.checkpointId, restoreContext)
        
        // Verify memory was restored
        val restoredFacts = restoreMemoryProvider.load(concept, MemorySubject.Everything, MemoryScope.CrossProduct)
        assertEquals(1, restoredFacts.size, "Should restore memory facts")
        assertEquals("original value", (restoredFacts.first() as SingleFact).value)
    }

    @Test
    fun `Persistency rollback returns custom data with correct type`() = runTest {
        val mockMemoryProvider = MockMemoryProvider()
        val mockStorageProvider = MockStorageProvider()
        val customData = buildJsonObject {
            put("testKey", "testValue")
            put("number", 42)
        }
        
        // Create checkpoint with custom data
        val checkpoint = AgentCheckpointData(
            checkpointId = Uuid.random().toString(),
            createdAt = Clock.System.now(),
            nodeId = "test-node",
            lastInput = JsonPrimitive("test-input"),
            messageHistory = emptyList(),
            extraSnapshotData = customData
        )
        mockStorageProvider.savedCheckpoints.add(checkpoint)
        
        val config = PersistencyFeatureConfig().apply {
            storage = mockStorageProvider
        }
        val persistency = Persistency(mockStorageProvider, config)
        val mockContext = MockAgentContext("test-agent", mockMemoryProvider)
        
        // Test generic return type
        val extraData = persistency.rollbackToCheckpoint<kotlinx.serialization.json.JsonObject>(
            checkpoint.checkpointId,
            mockContext
        )
        
        assertNotNull(extraData, "Should return custom data")
        assertEquals("testValue", extraData["testKey"]?.jsonPrimitive?.content)
        assertEquals(42, extraData["number"]?.jsonPrimitive?.int)
    }
}

// Mock implementations for testing
private class MockStorageProvider : PersistencyStorageProvider {
    val savedCheckpoints = mutableListOf<AgentCheckpointData>()
    
    override suspend fun getCheckpoints(): List<AgentCheckpointData> = savedCheckpoints
    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        savedCheckpoints.add(agentCheckpointData)
    }
    override suspend fun getLatestCheckpoint(): AgentCheckpointData? = savedCheckpoints.lastOrNull()
}

private class MockMemoryProvider : AgentMemoryProvider {
    private val storage = mutableMapOf<Triple<String, String, String>, MutableList<Fact>>()
    
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        val key = Triple(fact.concept.keyword, subject.name, scope.toString())
        storage.getOrPut(key) { mutableListOf() }.add(fact)
    }
    
    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val key = Triple(concept.keyword, subject.name, scope.toString())
        return storage[key] ?: emptyList()
    }
    
    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        return storage.entries
            .filter { (key, _) -> key.second == subject.name && key.third == scope.toString() }
            .flatMap { it.value }
    }
    
    override suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        return loadAll(subject, scope).filter { it.concept.description.contains(description, ignoreCase = true) }
    }
}

private class MockAgentContext(
    override val id: String,
    private val memoryProvider: AgentMemoryProvider
) : ai.koog.agents.core.agent.context.AIAgentContextBase {
    
    // Minimal mock implementation - only what's needed for testing
    private val contextData = mutableMapOf<String, Any>()
    
    override val llm: ai.koog.agents.core.agent.context.AIAgentLLMContext
        get() = throw NotImplementedError("Not needed for this test")
    
    fun memory(): ai.koog.agents.memory.feature.AgentMemory {
        // Return mock that provides access to our test memory provider
        return object : ai.koog.agents.memory.feature.AgentMemory(
            memoryProvider,
            llm,
            ai.koog.agents.memory.config.MemoryScopesProfile()
        ) {
            val agentMemory: AgentMemoryProvider = memoryProvider
        }
    }
    
    fun persistency(): Persistency {
        return contextData["persistency"] as Persistency
    }
    
    fun store(data: ai.koog.agents.core.agent.context.AgentContextData) {
        contextData["contextData"] = data
    }
    
    // Set persistency for testing
    fun setPersistency(persistency: Persistency) {
        contextData["persistency"] = persistency
    }
}