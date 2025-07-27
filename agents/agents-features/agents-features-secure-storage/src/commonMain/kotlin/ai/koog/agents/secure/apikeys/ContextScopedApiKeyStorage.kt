package ai.koog.agents.secure.apikeys

/**
 * Context-scoped wrapper for SecureApiKeyStorage that automatically applies a context to all operations.
 * 
 * This class provides a convenient way to scope all API key operations to a specific context
 * (e.g., user, agent, or tenant) without having to pass the context parameter repeatedly.
 * 
 * **Usage:**
 * ```kotlin
 * val userStorage = ContextScopedApiKeyStorage(baseStorage, "user:alice")
 * userStorage.saveApiKey("openai", "sk-...")  // Automatically saved with "user:alice" context
 * val key = userStorage.getApiKey("openai")   // Automatically retrieved with "user:alice" context
 * ```
 */
public class ContextScopedApiKeyStorage(
    private val baseStorage: SecureApiKeyStorage,
    private val context: String
) : SecureApiKeyStorage {
    
    override suspend fun saveApiKey(service: String, apiKey: String, context: String?) {
        val effectiveContext = context ?: this.context
        baseStorage.saveApiKey(service, apiKey, effectiveContext)
    }
    
    override suspend fun getApiKey(service: String, context: String?): String? {
        val effectiveContext = context ?: this.context
        return baseStorage.getApiKey(service, effectiveContext)
    }
    
    override suspend fun deleteApiKey(service: String, context: String?): Boolean {
        val effectiveContext = context ?: this.context
        return baseStorage.deleteApiKey(service, effectiveContext)
    }
    
    override suspend fun listServices(context: String?): List<String> {
        val effectiveContext = context ?: this.context
        return baseStorage.listServices(effectiveContext)
    }
    
    override suspend fun rotateApiKey(service: String, newApiKey: String, context: String?) {
        val effectiveContext = context ?: this.context
        baseStorage.rotateApiKey(service, newApiKey, effectiveContext)
    }
    
    override suspend fun hasApiKey(service: String, context: String?): Boolean {
        val effectiveContext = context ?: this.context
        return baseStorage.hasApiKey(service, effectiveContext)
    }
}