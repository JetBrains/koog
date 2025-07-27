package ai.koog.agents.secure.apikeys

import ai.koog.agents.secure.storage.impl.EncryptedKVStorage

/**
 * Implementation of SecureApiKeyStorage using the existing EncryptedKVStorage backend.
 * 
 * This implementation builds on our secure storage infrastructure to provide
 * encrypted API key management with context scoping and hierarchical key resolution.
 * 
 * **Key Storage Format:**
 * - Keys are stored with prefixed identifiers: "apikey:{service}:{context}"
 * - Global keys use format: "apikey:{service}"
 * - Context ensures isolation between users/agents
 * 
 * **Security:**
 * - All API keys encrypted using AES-256-GCM
 * - Uses the same encryption key as the underlying storage
 * - Context scoping prevents cross-contamination
 * 
 * @param storage The underlying encrypted storage backend
 */
public class SecureApiKeyStorageImpl(
    private val storage: EncryptedKVStorage
) : SecureApiKeyStorage {
    
    public companion object {
        private const val API_KEY_PREFIX = "apikey"
        private const val SEPARATOR = ":"
    }
    
    override suspend fun saveApiKey(service: String, apiKey: String, context: String?) {
        require(service.isNotBlank()) { "Service name cannot be blank" }
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        
        val storageKey = buildStorageKey(service, context)
        storage.put(storageKey, apiKey)
    }
    
    override suspend fun getApiKey(service: String, context: String?): String? {
        require(service.isNotBlank()) { "Service name cannot be blank" }
        
        val storageKey = buildStorageKey(service, context)
        return storage.get(storageKey)
    }
    
    override suspend fun deleteApiKey(service: String, context: String?): Boolean {
        require(service.isNotBlank()) { "Service name cannot be blank" }
        
        val storageKey = buildStorageKey(service, context)
        val existed = storage.get(storageKey) != null
        if (existed) {
            storage.delete(storageKey)
        }
        return existed
    }
    
    override suspend fun listServices(context: String?): List<String> {
        val prefix = if (context != null) {
            "$API_KEY_PREFIX$SEPARATOR*$SEPARATOR$context"
        } else {
            "$API_KEY_PREFIX$SEPARATOR*"
        }
        
        return storage.keys(prefix)
            .mapNotNull { key -> extractServiceFromKey(key, context) }
            .distinct()
    }
    
    override suspend fun rotateApiKey(service: String, newApiKey: String, context: String?) {
        require(service.isNotBlank()) { "Service name cannot be blank" }
        require(newApiKey.isNotBlank()) { "New API key cannot be blank" }
        
        // Atomic replacement - if this fails, old key remains intact
        saveApiKey(service, newApiKey, context)
    }
    
    override suspend fun hasApiKey(service: String, context: String?): Boolean {
        require(service.isNotBlank()) { "Service name cannot be blank" }
        
        val storageKey = buildStorageKey(service, context)
        return storage.get(storageKey) != null
    }
    
    /**
     * Builds the storage key for an API key with optional context.
     * 
     * Format: "apikey:{service}:{context}" or "apikey:{service}" for global keys
     */
    private fun buildStorageKey(service: String, context: String?): String {
        return if (context != null) {
            "$API_KEY_PREFIX$SEPARATOR$service$SEPARATOR$context"
        } else {
            "$API_KEY_PREFIX$SEPARATOR$service"
        }
    }
    
    /**
     * Extracts the service name from a storage key.
     * 
     * @param key The storage key (e.g., "apikey:openai:user:alice")
     * @param expectedContext The expected context, if any
     * @return The service name or null if the key doesn't match the expected format
     */
    private fun extractServiceFromKey(key: String, expectedContext: String?): String? {
        val parts = key.split(SEPARATOR)
        
        if (parts.size < 2 || parts[0] != API_KEY_PREFIX) {
            return null
        }
        
        return when {
            expectedContext == null && parts.size == 2 -> parts[1] // Global key: "apikey:service"
            expectedContext != null && parts.size >= 3 -> {
                val context = parts.drop(2).joinToString(SEPARATOR)
                if (context == expectedContext) parts[1] else null
            }
            else -> null
        }
    }
}