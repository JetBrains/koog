package ai.koog.agents.memory.providers

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.storage.Aes256GCMEncryptor
import ai.koog.agents.memory.storage.EncryptedStorage
import ai.koog.agents.memory.storage.SimpleStorage
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.rag.base.files.writeText
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private typealias FSProvider<Path> = FileSystemProvider.ReadWrite<Path>

class LocalFileMemoryProviderTest {
    @Serializable
    data object UserSubject : MemorySubject() {
        override val name: String = "user"
        override val promptDescription: String = "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
        override val priorityLevel: Int = 2
    }
    private val testKey = Aes256GCMEncryptor.run {
        keyToString(generateRandomKey())
    }

    private fun createTestStorage(fs: FSProvider<Path>): EncryptedStorage<Path> {
        val encryptor = Aes256GCMEncryptor(testKey)
        return EncryptedStorage(fs, encryptor)
    }

    @Test
    fun testDifferentMemoryScopes(@TempDir tempDir: Path) = runTest {
        val fs = JVMFileSystemProvider.ReadWrite
        val storage = createTestStorage(fs)
        val config = LocalMemoryConfig("memory-storage")
        val provider = LocalFileMemoryProvider(config, storage, fs, tempDir)

        val subject = UserSubject
        val concept = Concept(
            keyword = "test-concept",
            description = "Test concept description",
            factType = FactType.SINGLE
        )
        val fact = SingleFact(concept = concept, value = "Test fact content", timestamp = System.currentTimeMillis())

        // Test Agent scope
        val agentScope = MemoryScope.Agent("test-agent")
        provider.save(fact, subject, agentScope)
        val agentFacts = provider.load(concept, subject, agentScope)
        assertEquals(1, agentFacts.size)
        assertEquals(fact, agentFacts[0])

        // Test Feature scope
        val featureScope = MemoryScope.Feature("test-feature")
        provider.save(fact, subject, featureScope)
        val featureFacts = provider.load(concept, subject, featureScope)
        assertEquals(1, featureFacts.size)
        assertEquals(fact, featureFacts[0])

        // Test Product scope
        val productScope = MemoryScope.Product("test-product")
        provider.save(fact, subject, productScope)
        val productFacts = provider.load(concept, subject, productScope)
        assertEquals(1, productFacts.size)
        assertEquals(fact, productFacts[0])

        // Test CrossProduct scope
        provider.save(fact, subject, MemoryScope.CrossProduct)
        val crossProductFacts = provider.load(concept, subject, MemoryScope.CrossProduct)
        assertEquals(1, crossProductFacts.size)
        assertEquals(fact, crossProductFacts[0])

        // Verify facts are stored in different locations
        val allAgentFacts = provider.loadAll(subject, agentScope)
        val allFeatureFacts = provider.loadAll(subject, featureScope)
        val allProductFacts = provider.loadAll(subject, productScope)
        val allCrossProductFacts = provider.loadAll(subject, MemoryScope.CrossProduct)

        assertEquals(1, allAgentFacts.size)
        assertEquals(1, allFeatureFacts.size)
        assertEquals(1, allProductFacts.size)
        assertEquals(1, allCrossProductFacts.size)
    }

    @Test
    fun testFactOperations(@TempDir tempDir: Path) = runTest {
        val fs = JVMFileSystemProvider.ReadWrite
        val storage = createTestStorage(fs)
        val config = LocalMemoryConfig("memory-storage")
        val provider = LocalFileMemoryProvider(config, storage, fs, tempDir)

        val subject = UserSubject
        val scope = MemoryScope.Agent("test-agent")

        // Test saving and loading multiple facts for the same concept
        val concept1 = Concept(
            keyword = "concept1",
            description = "First concept",
            factType = FactType.SINGLE
        )
        val timestamp = System.currentTimeMillis()
        val fact1 = SingleFact(concept = concept1, value = "Fact 1 content", timestamp = timestamp)
        val fact2 = SingleFact(concept = concept1, value = "Fact 2 content", timestamp = timestamp)

        provider.save(fact1, subject, scope)
        provider.save(fact2, subject, scope)

        val loadedFacts = provider.load(concept1, subject, scope)
        assertEquals(2, loadedFacts.size)
        loadedFacts.forEach { assertIs<SingleFact>(it) }
        assertTrue(loadedFacts.contains(fact1))
        assertTrue(loadedFacts.contains(fact2))

        // Test loading by description
        val concept2 = Concept(
            keyword = "concept2",
            description = "Second concept with special keyword",
            factType = FactType.SINGLE
        )
        val fact3 = SingleFact(concept = concept2, value = "Fact 3 content", timestamp = timestamp)
        provider.save(fact3, subject, scope)

        val foundFacts = provider.loadByDescription("special keyword", subject, scope)
        assertEquals(1, foundFacts.size)
        assertIs<SingleFact>(foundFacts[0])
        assertEquals(fact3, foundFacts[0])

        // Test loading all facts
        val allFacts = provider.loadAll(subject, scope)
        assertEquals(3, allFacts.size)
        allFacts.forEach { assertIs<SingleFact>(it) }
        assertTrue(allFacts.contains(fact1))
        assertTrue(allFacts.contains(fact2))
        assertTrue(allFacts.contains(fact3))
    }

    @Test
    fun testCorruptedJsonHandling(@TempDir tempDir: Path) = runTest {
        val fs = JVMFileSystemProvider.ReadWrite
        val storage = SimpleStorage(fs)
        val config = LocalMemoryConfig("memory-storage")
        val provider = LocalFileMemoryProvider(config, storage, fs, tempDir)

        val subject = UserSubject
        val scope = MemoryScope.Agent("test-agent")
        val concept = Concept(
            keyword = "test-concept",
            description = "Test concept description",
            factType = FactType.SINGLE
        )
        val fact = SingleFact(concept = concept, value = "Test fact content", timestamp = System.currentTimeMillis())

        // First, save a valid fact
        provider.save(fact, subject, scope)
        val validFacts = provider.load(concept, subject, scope)
        assertEquals(1, validFacts.size)
        assertEquals(fact, validFacts[0])

        // Now corrupt the JSON file by writing invalid JSON directly
        val storagePath = getStoragePathForTest(config, subject, scope, tempDir)
        val corruptedJson = """{"invalid": json, "missing": quotes, "broken": syntax}"""
        fs.writeText(storagePath, corruptedJson)

        // The provider should handle corrupted JSON gracefully and return empty results
        val corruptedFacts = provider.load(concept, subject, scope)
        assertEquals(0, corruptedFacts.size)

        // Verify that loadAll also handles corrupted JSON gracefully
        val allCorruptedFacts = provider.loadAll(subject, scope)
        assertEquals(0, allCorruptedFacts.size)

        // Verify that loadByDescription also handles corrupted JSON gracefully
        val descriptionFacts = provider.loadByDescription("test", subject, scope)
        assertEquals(0, descriptionFacts.size)
    }

    private fun getStoragePathForTest(
        config: LocalMemoryConfig,
        subject: MemorySubject,
        scope: MemoryScope,
        root: Path
    ): Path {
        val fs = JVMFileSystemProvider.ReadWrite
        val segments = listOf(config.storageDirectory) + when (scope) {
            is MemoryScope.Agent -> listOf("agent", scope.name, "subject", subject.name)
            is MemoryScope.Feature -> listOf("feature", scope.id, "subject", subject.name)
            is MemoryScope.Product -> listOf("product", scope.name, "subject", subject.name)
            MemoryScope.CrossProduct -> listOf("organization", "subject", subject.name)
        }
        return segments.fold(root) { acc, segment -> fs.joinPath(acc, segment) }
    }
}
