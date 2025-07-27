package ai.koog.agents.secure.apikeys

/**
 * Multi-level API key resolver with hierarchical fallback support.
 * 
 * This resolver implements a fallback hierarchy for API key resolution:
 * 1. **User-scoped keys**: Keys stored for specific users/contexts
 * 2. **Agent-scoped keys**: Keys stored for specific agent instances  
 * 3. **Global user keys**: User's global keys (no context)
 * 4. **Environment keys**: Keys from environment variables or config
 * 
 * **Use Cases:**
 * - Multi-tenant SaaS: User keys override app keys
 * - Development: Env vars as fallback for missing user keys
 * - Enterprise: Agent-specific keys for different departments
 * - Testing: Override production keys with test keys
 * 
 * **Example Hierarchy:**
 * ```
 * user:alice + agent:assistant -> Look for:
 * 1. apikey:openai:user:alice:agent:assistant
 * 2. apikey:openai:user:alice  
 * 3. apikey:openai:agent:assistant
 * 4. apikey:openai
 * 5. OPENAI_API_KEY env var
 * ```
 */
public class ApiKeyResolver(
    private val secureStorage: SecureApiKeyStorage,
    private val environmentProvider: EnvironmentApiKeyProvider = EnvironmentApiKeyProvider()
) {
    
    /**
     * Resolves an API key using the hierarchical fallback system.
     * 
     * @param service The service identifier (e.g., "openai", "anthropic")
     * @param userContext Optional user context (e.g., "user:alice")
     * @param agentContext Optional agent context (e.g., "agent:assistant-v1")
     * @return The resolved API key or null if not found in any source
     */
    public suspend fun resolveApiKey(
        service: String,
        userContext: String? = null,
        agentContext: String? = null
    ): String? {
        // 1. Try user + agent scoped key
        if (userContext != null && agentContext != null) {
            val combinedContext = "$userContext:$agentContext"
            secureStorage.getApiKey(service, combinedContext)?.let { return it }
        }
        
        // 2. Try user-scoped key
        if (userContext != null) {
            secureStorage.getApiKey(service, userContext)?.let { return it }
        }
        
        // 3. Try agent-scoped key  
        if (agentContext != null) {
            secureStorage.getApiKey(service, agentContext)?.let { return it }
        }
        
        // 4. Try global user key (no context)
        secureStorage.getApiKey(service)?.let { return it }
        
        // 5. Fall back to environment
        return environmentProvider.getApiKey(service)
    }
    
    /**
     * Checks if an API key is available through any source in the hierarchy.
     * 
     * @param service The service identifier
     * @param userContext Optional user context
     * @param agentContext Optional agent context
     * @return true if a key is available from any source
     */
    public suspend fun hasApiKey(
        service: String,
        userContext: String? = null,
        agentContext: String? = null
    ): Boolean {
        return resolveApiKey(service, userContext, agentContext) != null
    }
    
    /**
     * Gets information about where an API key would be resolved from.
     * Useful for debugging and user feedback.
     * 
     * @param service The service identifier
     * @param userContext Optional user context
     * @param agentContext Optional agent context
     * @return Information about the key source, or null if no key found
     */
    public suspend fun getApiKeySource(
        service: String,
        userContext: String? = null,
        agentContext: String? = null
    ): ApiKeySource? {
        // Check each source in order
        if (userContext != null && agentContext != null) {
            val combinedContext = "$userContext:$agentContext"
            if (secureStorage.hasApiKey(service, combinedContext)) {
                return ApiKeySource.UserAgent(userContext, agentContext)
            }
        }
        
        if (userContext != null && secureStorage.hasApiKey(service, userContext)) {
            return ApiKeySource.User(userContext)
        }
        
        if (agentContext != null && secureStorage.hasApiKey(service, agentContext)) {
            return ApiKeySource.Agent(agentContext)
        }
        
        if (secureStorage.hasApiKey(service)) {
            return ApiKeySource.Global
        }
        
        if (environmentProvider.hasApiKey(service)) {
            return ApiKeySource.Environment
        }
        
        return null
    }
}

/**
 * Represents the source of an API key in the resolution hierarchy.
 */
public sealed class ApiKeySource {
    /** Key from user + agent specific storage */
    public data class UserAgent(val userContext: String, val agentContext: String) : ApiKeySource()
    
    /** Key from user-specific storage */  
    public data class User(val userContext: String) : ApiKeySource()
    
    /** Key from agent-specific storage */
    public data class Agent(val agentContext: String) : ApiKeySource()
    
    /** Key from global secure storage */
    public object Global : ApiKeySource()
    
    /** Key from environment variables */
    public object Environment : ApiKeySource()
    
    override fun toString(): String = when (this) {
        is UserAgent -> "User+Agent($userContext + $agentContext)"
        is User -> "User($userContext)"
        is Agent -> "Agent($agentContext)"
        is Global -> "Global"
        is Environment -> "Environment"
    }
}

/**
 * Provider for API keys from environment variables and configuration.
 * 
 * This class handles the fallback to environment-based API keys when
 * no user-provided keys are available in secure storage.
 */
public open class EnvironmentApiKeyProvider {
    
    /**
     * Gets an API key from environment variables.
     * 
     * Uses common naming conventions:
     * - "openai" -> OPENAI_API_KEY
     * - "anthropic" -> ANTHROPIC_API_KEY  
     * - "github" -> GITHUB_API_KEY or GITHUB_TOKEN
     */
    public open fun getApiKey(service: String): String? {
        return when (service.lowercase()) {
            "openai" -> getEnvVar("OPENAI_API_KEY")
            "anthropic" -> getEnvVar("ANTHROPIC_API_KEY") 
            "github" -> getEnvVar("GITHUB_API_KEY") ?: getEnvVar("GITHUB_TOKEN")
            "slack" -> getEnvVar("SLACK_API_KEY") ?: getEnvVar("SLACK_BOT_TOKEN")
            "discord" -> getEnvVar("DISCORD_API_KEY") ?: getEnvVar("DISCORD_BOT_TOKEN")
            "google" -> getEnvVar("GOOGLE_API_KEY")
            "huggingface" -> getEnvVar("HUGGINGFACE_API_KEY") ?: getEnvVar("HF_TOKEN")
            else -> {
                // Try standard format: {SERVICE}_API_KEY
                val standardKey = "${service.uppercase()}_API_KEY"
                getEnvVar(standardKey)
            }
        }
    }
    
    /**
     * Checks if an API key is available from environment variables.
     */
    public open fun hasApiKey(service: String): Boolean {
        return getApiKey(service) != null
    }
    
    /**
     * Gets an environment variable value.
     * Platform-specific implementation would be needed for actual env access.
     */
    private fun getEnvVar(name: String): String? {
        // TODO: This would need platform-specific implementation
        // For now, returning null as placeholder
        return null // System.getenv(name) on JVM, process.env on JS, etc.
    }
}