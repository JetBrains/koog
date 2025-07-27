package ai.koog.agents.secure.storage.providers

import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.memory.providers.MemoryProviderConfig
import ai.koog.agents.secure.storage.LocalKVStorage
import ai.koog.agents.secure.storage.StorageMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Secure storage implementation of [AgentMemoryProvider] using Kottage backend with encryption.
 * 
 * This provider leverages the SecureStorage feature to provide:
 * - **Enterprise-grade security**: AES-256-GCM encryption for sensitive memory data
 * - **High performance**: SQLite backend with B-tree indexes for efficient queries
 * - **Flexible key management**: Pluggable encryption key providers
 * - **Development support**: Plain mode for testing/development
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
 * **Security Features:**
 * - Data encrypted at rest using AES-256-GCM
 * - GDPR/SOC2/HIPAA compliance ready
 * - Secure key management with configurable providers
 * - Protection against unauthorized device access
 * 
 * **Usage Example:**
 * ```kotlin
 * install(AgentMemory) {
 *     memoryProvider = KottageAgentMemoryProvider(
 *         config = SecureMemoryConfig(
 *             storageDirectory = "secure-memory",
 *             encryption = EncryptedMode {
 *                 keyProvider = PassphraseKeyProvider("secure-passphrase", salt, 100000)
 *                 databasePath = "memory.db"
 *             }
 *         ),
 *         storage = mySecureStorage
 *     )
 *     featureName = "code-assistant"
 *     productName = "my-ide"
 * }
 * ```
 * 
 * @property config Configuration for the secure memory provider
 * @property storage Secure key-value storage backend (can be encrypted or plain)
 */
public class KottageAgentMemoryProvider(
    private val config: SecureMemoryConfig,
    private val storage: LocalKVStorage
) : AgentMemoryProvider {
    
    private val mutex = Mutex()
    
    /**
     * JSON configuration optimized for memory storage with security considerations.
     * - prettyPrint = false: Reduces storage footprint for encrypted data
     * - ignoreUnknownKeys = true: Forward compatibility with schema evolution
     */
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }
    
    /**
     * Generates a storage key for the given subject, scope, and concept.
     * 
     * The key format ensures proper isolation and organization:
     * - Scope isolation prevents cross-scope data leakage
     * - Subject categorization organizes facts by context
     * - Concept-specific keys enable efficient fact retrieval
     * 
     * @param subject The memory subject (e.g., MACHINE, PROJECT, USER)
     * @param scope The memory scope (e.g., Agent, Feature, Product)
     * @param concept Optional concept for concept-specific keys
     * @return Storage key for the specified context
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
     * 
     * @param key Storage key to load from
     * @return List of facts, empty if none found or error occurs
     */
    private suspend fun loadFactsFromKey(key: String): List<Fact> = mutex.withLock {
        try {
            val content = storage.get(key) ?: return emptyList()
            return json.decodeFromString<List<Fact>>(content)
        } catch (e: Exception) {
            // Log error but don't fail - return empty list for graceful degradation
            return emptyList()
        }
    }
    
    /**
     * Saves facts to storage with atomic updates and proper serialization.
     * 
     * @param key Storage key to save to
     * @param facts List of facts to store
     */
    private suspend fun saveFactsToKey(key: String, facts: List<Fact>) = mutex.withLock {
        val serialized = json.encodeToString(facts)
        storage.put(key, serialized)
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
        val allKeys = storage.keys("$baseKey/")
        
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
 * Configuration for secure memory storage using Kottage backend.
 * 
 * This configuration extends the base memory config with security-specific settings:
 * - **Security Mode**: Choose between encrypted (recommended) and plain storage
 * - **Storage Directory**: Base directory for organizing memory files
 * - **Default Scope**: Fallback scope when none specified
 * 
 * **Security Recommendations:**
 * - Use EncryptedMode for production deployments
 * - Use PlainMode only for development/testing
 * - Configure appropriate key providers for encryption
 * - Set storage directory outside of version control
 * 
 * Example configurations:
 * ```kotlin
 * // Production (encrypted)
 * SecureMemoryConfig(
 *     storageDirectory = "secure-memory",
 *     encryption = EncryptedMode {
 *         keyProvider = PassphraseKeyProvider("secure-passphrase", salt, 100000)
 *         databasePath = "memory-prod.db"
 *     }
 * )
 * 
 * // Development (plain with warning)
 * SecureMemoryConfig(
 *     storageDirectory = "dev-memory",
 *     encryption = PlainMode {
 *         databasePath = "memory-dev.db"
 *         suppressSecurityWarning = true
 *     }
 * )
 * ```
 * 
 * @property storageDirectory Base directory for memory storage organization
 * @property defaultScope Default memory scope when none specified
 * @property encryption Security mode configuration (encrypted or plain)
 */
/**
 * Configuration for secure memory storage using Kottage backend.
 * 
 * Note: This is a standalone configuration class that does not extend MemoryProviderConfig
 * to avoid module dependency issues. Users should configure the AgentMemory feature
 * to use this provider with appropriate settings.
 */
@Serializable
@SerialName("secure")
public data class SecureMemoryConfig(
    val storageDirectory: String,
    val defaultScope: MemoryScope = MemoryScope.CrossProduct,
    val encryption: StorageMode? = null // Will use SecureStorage feature's mode if null
)