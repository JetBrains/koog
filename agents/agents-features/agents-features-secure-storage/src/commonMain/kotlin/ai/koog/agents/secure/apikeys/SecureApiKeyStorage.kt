package ai.koog.agents.secure.apikeys

/**
 * Secure storage interface for API keys with context scoping and multi-level fallback support.
 * 
 * This interface provides secure encrypted storage for user-provided API keys, enabling:
 * - **Per-user API key storage**: Users can provide their own OpenAI, Anthropic, etc. keys
 * - **Context scoping**: Keys can be scoped to specific agents or applications  
 * - **Fallback hierarchy**: User keys → Agent keys → Environment keys
 * - **Key rotation**: Safe update and rotation of API keys
 * 
 * **Use Cases:**
 * - SaaS applications where users provide their own API keys
 * - Multi-tenant agent deployments with per-user secrets
 * - Enterprise environments with scoped key access
 * - Development environments with fallback to env vars
 * 
 * **Security Features:**
 * - All keys encrypted using AES-256-GCM
 * - Optional context isolation prevents key leakage between agents
 * - Secure key derivation from user passphrases or environment
 * 
 * **Example Usage:**
 * ```kotlin
 * // Store user's OpenAI key
 * apiKeyStorage.saveApiKey("openai", "sk-...", context = "user:alice")
 * 
 * // Retrieve with fallback
 * val key = apiKeyStorage.getApiKey("openai", context = "user:alice") 
 *           ?: apiKeyStorage.getApiKey("openai") // Global fallback
 * ```
 */
public interface SecureApiKeyStorage {
    
    /**
     * Saves an API key for a specific service with optional context scoping.
     * 
     * @param service The service identifier (e.g., "openai", "anthropic", "github")
     * @param apiKey The API key to store securely
     * @param context Optional context for scoping (e.g., "user:alice", "agent:assistant-v1")
     * @throws SecurityException if encryption fails
     */
    public suspend fun saveApiKey(service: String, apiKey: String, context: String? = null)
    
    /**
     * Retrieves an API key for a service with optional context scoping.
     * 
     * @param service The service identifier
     * @param context Optional context for scoping
     * @return The decrypted API key or null if not found
     * @throws SecurityException if decryption fails
     */
    public suspend fun getApiKey(service: String, context: String? = null): String?
    
    /**
     * Deletes an API key for a service with optional context scoping.
     * 
     * @param service The service identifier
     * @param context Optional context for scoping
     * @return true if the key was deleted, false if it didn't exist
     */
    public suspend fun deleteApiKey(service: String, context: String? = null): Boolean
    
    /**
     * Lists all services that have API keys stored, optionally filtered by context.
     * 
     * @param context Optional context filter
     * @return List of service identifiers
     */
    public suspend fun listServices(context: String? = null): List<String>
    
    /**
     * Rotates an API key by replacing the old key with a new one atomically.
     * 
     * @param service The service identifier
     * @param newApiKey The new API key to replace the old one
     * @param context Optional context for scoping
     * @throws SecurityException if the operation fails
     */
    public suspend fun rotateApiKey(service: String, newApiKey: String, context: String? = null)
    
    /**
     * Checks if an API key exists for a service and context.
     * 
     * @param service The service identifier
     * @param context Optional context for scoping
     * @return true if the key exists, false otherwise
     */
    public suspend fun hasApiKey(service: String, context: String? = null): Boolean
}