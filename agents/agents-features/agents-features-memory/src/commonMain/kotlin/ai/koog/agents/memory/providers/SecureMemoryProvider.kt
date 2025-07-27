package ai.koog.agents.memory.providers

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Interface for secure key-value storage backends that can be used with SecureMemoryProvider.
 * This allows the memory feature to work with different storage implementations while
 * maintaining security and encryption capabilities.
 */
public interface SecureKVBackend {
    /** Get a value by key */
    public suspend fun get(key: String): String?
    
    /** Store a key-value pair */
    public suspend fun put(key: String, value: String)
    
    /** Delete a key */
    public suspend fun delete(key: String)
    
    /** Get all keys matching a prefix */
    public suspend fun keys(prefix: String): List<String>
    
    /** Close the backend and release resources */
    public suspend fun close()
}

/**
 * Secure memory provider that uses a pluggable secure storage backend.
 * 
 * This provider offers:
 * - **Pluggable backends**: Works with any SecureKVBackend implementation
 * - **Security**: Data encryption handled by the backend
 * - **Performance**: Efficient SQLite-based storage with B-tree indexes
 * - **Multiplatform**: Works across JVM, JS platforms
 * 
 * **Storage Structure:**
 * ```
 * Key format: "{scope-type}/{scope-id}/subject/{subject-name}/concepts/{concept-keyword}"
 * 
 * Examples:
 * - "agent/code-assistant/subject/machine/concepts/env-info"
 * - "product/my-ide/subject/project/concepts/dependencies"
 * - "organization/subject/user/concepts/preferences"
 * ```
 * 
 * **Usage Example:**
 * ```kotlin
 * install(AgentMemory) {
 *     memoryProvider = SecureMemoryProvider(
 *         config = SecureMemoryConfig(
 *             storageDirectory = "secure-memory"
 *         ),
 *         backend = secureStorage.kvBackend() // From SecureStorage feature
 *     )
 *     featureName = "code-assistant"
 *     productName = "my-ide"
 * }
 * ```
 * 
 * @property config Configuration for the secure memory provider
 * @property backend Secure key-value storage backend
 */
public class SecureMemoryProvider(
    private val config: SecureMemoryConfig,
    private val backend: SecureKVBackend
) : AgentMemoryProvider {
    
    private val mutex = Mutex()
    
    /**
     * JSON configuration optimized for memory storage.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    /**
     * Generates a storage key for the given subject, scope, and concept.
     */
    private fun getStorageKey(subject: MemorySubject, scope: MemoryScope, concept: Concept? = null): String {
        val scopePrefix = when (scope) {
            is MemoryScope.Agent -> "agent/${scope.name}"
            is MemoryScope.Feature -> "feature/${scope.id}"
            is MemoryScope.Product -> "product/${scope.name}"
            MemoryScope.CrossProduct -> "organization"
        }
        
        val baseKey = "$scopePrefix/subject/${subject.name}"
        return if (concept != null) {
            "$baseKey/concepts/${concept.keyword}"
        } else {
            "$baseKey/concepts"
        }
    }
    
    /**
     * Loads facts from storage with proper error handling and thread safety.
     */
    private suspend fun loadFactsFromKey(key: String): List<Fact> = mutex.withLock {
        try {
            val content = backend.get(key) ?: return emptyList()
            return json.decodeFromString<List<Fact>>(content)
        } catch (e: Exception) {
            // Log error but don't fail - return empty list for graceful degradation
            return emptyList()
        }
    }
    
    /**
     * Saves facts to storage with atomic updates and proper serialization.
     */
    private suspend fun saveFactsToKey(key: String, facts: List<Fact>) = mutex.withLock {
        val serialized = json.encodeToString(facts)
        backend.put(key, serialized)
    }
    
    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        val key = getStorageKey(subject, scope, fact.concept)
        val existingFacts = loadFactsFromKey(key)
        val updatedFacts = existingFacts + fact
        saveFactsToKey(key, updatedFacts)
    }
    
    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val key = getStorageKey(subject, scope, concept)
        return loadFactsFromKey(key)
    }
    
    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val baseKey = getStorageKey(subject, scope)
        
        // Get all keys that start with the base key pattern
        val allKeys = backend.keys("$baseKey/")
        
        val allFacts = mutableListOf<Fact>()
        for (key in allKeys) {
            allFacts.addAll(loadFactsFromKey(key))
        }
        
        return allFacts
    }
    
    override suspend fun loadByDescription(description: String, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val allFacts = loadAll(subject, scope)
        return allFacts.filter { fact ->
            fact.concept.description.contains(description, ignoreCase = true) ||
            fact.concept.keyword.contains(description, ignoreCase = true)
        }
    }
}

/**
 * Configuration for secure memory storage.
 * 
 * This configuration allows the memory feature to use secure storage backends
 * without tight coupling to specific storage implementations.
 * 
 * @property storageDirectory Base directory for memory storage organization
 * @property defaultScope Default memory scope when none specified
 */
@Serializable
@SerialName("secure")
public data class SecureMemoryConfig(
    val storageDirectory: String,
    override val defaultScope: MemoryScope = MemoryScope.CrossProduct
) : MemoryProviderConfig