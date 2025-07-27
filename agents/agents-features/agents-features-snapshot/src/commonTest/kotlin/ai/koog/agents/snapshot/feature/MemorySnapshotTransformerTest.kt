package ai.koog.agents.snapshot.feature

import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.providers.AgentMemoryProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemorySnapshotTransformerTest {

    @Test
    fun `DefaultMemorySnapshotTransformer captures empty memory as null`() = runTest {
        val transformer = DefaultMemorySnapshotTransformer()
        val mockProvider = MockEmptyMemoryProvider()
        
        val snapshot = transformer.captureSnapshot(mockProvider)
        
        assertNull(snapshot, "Empty memory should result in null snapshot")
    }

    @Test
    fun `DefaultMemorySnapshotTransformer captures facts correctly`() = runTest {
        val transformer = DefaultMemorySnapshotTransformer()
        val mockProvider = MockMemoryProvider()
        
        // Add test facts
        val concept = Concept("test-concept", "Test concept description", FactType.SINGLE)
        val fact = SingleFact(concept, 1234567890L, "test value")
        mockProvider.save(fact, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        val snapshot = transformer.captureSnapshot(mockProvider)
        
        assertNotNull(snapshot, "Non-empty memory should produce snapshot")
        assertTrue(snapshot.containsKey("version"), "Snapshot should have version")
        assertTrue(snapshot.containsKey("subjects"), "Snapshot should have subjects")
        assertEquals("1.0", snapshot["version"]?.jsonPrimitive?.content)
    }

    @Test
    fun `DefaultMemorySnapshotTransformer restores facts correctly`() = runTest {
        val transformer = DefaultMemorySnapshotTransformer()
        val captureProvider = MockMemoryProvider()
        val restoreProvider = MockMemoryProvider()
        
        // Capture from one provider
        val concept = Concept("restore-test", "Restore test concept", FactType.SINGLE)
        val originalFact = SingleFact(concept, 1234567890L, "original value")
        captureProvider.save(originalFact, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        val snapshot = transformer.captureSnapshot(captureProvider)
        assertNotNull(snapshot)
        
        // Restore to different provider
        transformer.restoreSnapshot(restoreProvider, snapshot)
        
        val restoredFacts = restoreProvider.load(concept, MemorySubject.Everything, MemoryScope.CrossProduct)
        assertEquals(1, restoredFacts.size, "Should restore exactly one fact")
        
        val restoredFact = restoredFacts.first() as SingleFact
        assertEquals(originalFact.concept.keyword, restoredFact.concept.keyword)
        assertEquals(originalFact.value, restoredFact.value)
        assertEquals(originalFact.timestamp, restoredFact.timestamp)
    }

    @Test
    fun `DefaultMemorySnapshotTransformer validates compatibility correctly`() {
        val transformer = DefaultMemorySnapshotTransformer()
        
        // Valid snapshot
        val validSnapshot = buildJsonObject {
            put("version", "1.0")
            put("subjects", buildJsonObject {})
        }
        assertTrue(transformer.isSnapshotCompatible(validSnapshot))
        
        // Invalid version
        val invalidVersionSnapshot = buildJsonObject {
            put("version", "2.0")
            put("subjects", buildJsonObject {})
        }
        assertFalse(transformer.isSnapshotCompatible(invalidVersionSnapshot))
        
        // Missing subjects
        val missingSubjectsSnapshot = buildJsonObject {
            put("version", "1.0")
        }
        assertFalse(transformer.isSnapshotCompatible(missingSubjectsSnapshot))
    }

    @Test
    fun `DefaultMemorySnapshotTransformer handles multiple fact types`() = runTest {
        val transformer = DefaultMemorySnapshotTransformer()
        val provider = MockMemoryProvider()
        
        // Add single fact
        val singleConcept = Concept("single", "Single fact concept", FactType.SINGLE)
        val singleFact = SingleFact(singleConcept, 1234567890L, "single value")
        provider.save(singleFact, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        // Add multiple facts
        val multipleConcept = Concept("multiple", "Multiple facts concept", FactType.MULTIPLE)
        val multipleFacts = MultipleFacts(multipleConcept, 1234567890L, listOf("value1", "value2", "value3"))
        provider.save(multipleFacts, MemorySubject.Everything, MemoryScope.CrossProduct)
        
        val snapshot = transformer.captureSnapshot(provider)
        assertNotNull(snapshot)
        
        // Restore and verify
        val restoreProvider = MockMemoryProvider()
        transformer.restoreSnapshot(restoreProvider, snapshot)
        
        val restoredSingle = restoreProvider.load(singleConcept, MemorySubject.Everything, MemoryScope.CrossProduct)
        assertEquals(1, restoredSingle.size)
        assertEquals("single value", (restoredSingle.first() as SingleFact).value)
        
        val restoredMultiple = restoreProvider.load(multipleConcept, MemorySubject.Everything, MemoryScope.CrossProduct)
        assertEquals(1, restoredMultiple.size)
        assertEquals(3, (restoredMultiple.first() as MultipleFacts).values.size)
    }
}

// Mock implementations for testing
private class MockEmptyMemoryProvider : AgentMemoryProvider {
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {}
    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> = emptyList()
    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> = emptyList()
    override suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact> = emptyList()
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