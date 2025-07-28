package ai.koog.agents.secure.apikeys

/**
 * Type-safe extension functions for SecureApiKeyStorage that provide compile-time safety
 * and validation for API key operations.
 * 
 * These extensions use the ApiKeyProvider system to ensure:
 * - Compile-time error detection for typos
 * - Automatic API key format validation
 * - IDE autocomplete and refactoring support
 * - Clear documentation of supported providers
 */

/**
 * Type-safe API key saving with automatic validation.
 */
public suspend fun SecureApiKeyStorage.saveApiKey(
    provider: ApiKeyProvider,
    apiKey: String,
    context: String? = null,
    validateFormat: Boolean = true
) {
    if (validateFormat && !provider.validateKey(apiKey)) {
        throw InvalidApiKeyFormatException(
            provider = provider,
            providedKey = apiKey,
            message = "API key format is invalid for ${provider.displayName}. " +
                    "Expected pattern: ${provider.keyPattern?.pattern ?: "any format"}"
        )
    }
    
    saveApiKey(provider.serviceId, apiKey, context)
}

/**
 * Type-safe API key retrieval.
 */
public suspend fun SecureApiKeyStorage.getApiKey(
    provider: ApiKeyProvider,
    context: String? = null
): String? {
    return getApiKey(provider.serviceId, context)
}

/**
 * Type-safe API key removal.
 */
public suspend fun SecureApiKeyStorage.removeApiKey(
    provider: ApiKeyProvider,
    context: String? = null
): Boolean {
    return deleteApiKey(provider.serviceId, context)
}

/**
 * Type-safe API key existence check.
 */
public suspend fun SecureApiKeyStorage.hasApiKey(
    provider: ApiKeyProvider,
    context: String? = null
): Boolean {
    return hasApiKey(provider.serviceId, context)
}

/**
 * Type-safe API key rotation with validation.
 */
public suspend fun SecureApiKeyStorage.rotateApiKey(
    provider: ApiKeyProvider,
    newApiKey: String,
    context: String? = null,
    validateFormat: Boolean = true
) {
    if (validateFormat && !provider.validateKey(newApiKey)) {
        throw InvalidApiKeyFormatException(
            provider = provider,
            providedKey = newApiKey,
            message = "API key format is invalid for ${provider.displayName}. " +
                    "Expected pattern: ${provider.keyPattern?.pattern ?: "any format"}"
        )
    }
    rotateApiKey(provider.serviceId, newApiKey, context)
}

/**
 * Batch operations for multiple providers
 */
public suspend fun SecureApiKeyStorage.saveApiKeys(
    keys: Map<ApiKeyProvider, String>,
    context: String? = null,
    validateFormat: Boolean = true
) {
    for ((provider, apiKey) in keys) {
        saveApiKey(provider, apiKey, context, validateFormat)
    }
}

/**
 * Get all API keys for a list of providers.
 */
public suspend fun SecureApiKeyStorage.getApiKeys(
    providers: List<ApiKeyProvider>,
    context: String? = null
): Map<ApiKeyProvider, String?> {
    return providers.associateWith { provider ->
        getApiKey(provider, context)
    }
}

/**
 * Get all available API keys (those that exist) for a list of providers.
 */
public suspend fun SecureApiKeyStorage.getAvailableApiKeys(
    providers: List<ApiKeyProvider>,
    context: String? = null
): Map<ApiKeyProvider, String> {
    return providers.mapNotNull { provider ->
        getApiKey(provider, context)?.let { key ->
            provider to key
        }
    }.toMap()
}

/**
 * List all providers that have stored API keys.
 */
public suspend fun SecureApiKeyStorage.getProvidersWithKeys(
    context: String? = null
): List<ApiKeyProvider> {
    val allProviders = ApiKeyProviders.all
    return allProviders.filter { provider ->
        hasApiKey(provider, context)
    }
}

/**
 * Context-scoped storage for specific user/agent combinations.
 */
public class TypeSafeContextScopedApiKeyStorage(
    private val storage: SecureApiKeyStorage,
    private val context: String
) {
    
    public suspend fun saveApiKey(
        provider: ApiKeyProvider,
        apiKey: String,
        validateFormat: Boolean = true
    ) {
        storage.saveApiKey(provider, apiKey, context, validateFormat)
    }
    
    public suspend fun getApiKey(provider: ApiKeyProvider): String? {
        return storage.getApiKey(provider, context)
    }
    
    public suspend fun removeApiKey(provider: ApiKeyProvider): Boolean {
        return storage.removeApiKey(provider, context)
    }
    
    public suspend fun hasApiKey(provider: ApiKeyProvider): Boolean {
        return storage.hasApiKey(provider, context)
    }
    
    public suspend fun rotateApiKey(
        provider: ApiKeyProvider,
        newApiKey: String,
        validateFormat: Boolean = true
    ) {
        storage.rotateApiKey(provider, newApiKey, context, validateFormat)
    }
    
    public suspend fun saveApiKeys(
        keys: Map<ApiKeyProvider, String>,
        validateFormat: Boolean = true
    ) {
        storage.saveApiKeys(keys, context, validateFormat)
    }
    
    public suspend fun getAvailableApiKeys(
        providers: List<ApiKeyProvider> = ApiKeyProviders.all
    ): Map<ApiKeyProvider, String> {
        return storage.getAvailableApiKeys(providers, context)
    }
}

/**
 * Create a type-safe context-scoped API key storage.
 */
public fun SecureApiKeyStorage.scoped(context: String): TypeSafeContextScopedApiKeyStorage {
    return TypeSafeContextScopedApiKeyStorage(this, context)
}

/**
 * Convenience extensions for specific providers
 */

// OpenAI specific extensions
public suspend fun SecureApiKeyStorage.saveOpenAIKey(
    apiKey: String,
    context: String? = null
) = saveApiKey(ApiKeyProviders.OpenAI, apiKey, context)

public suspend fun SecureApiKeyStorage.getOpenAIKey(
    context: String? = null
): String? = getApiKey(ApiKeyProviders.OpenAI, context)

// Anthropic specific extensions  
public suspend fun SecureApiKeyStorage.saveAnthropicKey(
    apiKey: String,
    context: String? = null
) = saveApiKey(ApiKeyProviders.Anthropic, apiKey, context)

public suspend fun SecureApiKeyStorage.getAnthropicKey(
    context: String? = null
): String? = getApiKey(ApiKeyProviders.Anthropic, context)

// AWS specific extensions
public suspend fun SecureApiKeyStorage.saveAWSKey(
    apiKey: String,
    context: String? = null
) = saveApiKey(ApiKeyProviders.AWS, apiKey, context)

public suspend fun SecureApiKeyStorage.getAWSKey(
    context: String? = null
): String? = getApiKey(ApiKeyProviders.AWS, context)

// Stripe specific extensions
public suspend fun SecureApiKeyStorage.saveStripeKey(
    apiKey: String,
    context: String? = null
) = saveApiKey(ApiKeyProviders.Stripe, apiKey, context)

public suspend fun SecureApiKeyStorage.getStripeKey(
    context: String? = null
): String? = getApiKey(ApiKeyProviders.Stripe, context)

// GitHub specific extensions
public suspend fun SecureApiKeyStorage.saveGitHubKey(
    apiKey: String,
    context: String? = null
) = saveApiKey(ApiKeyProviders.GitHub, apiKey, context)

public suspend fun SecureApiKeyStorage.getGitHubKey(
    context: String? = null
): String? = getApiKey(ApiKeyProviders.GitHub, context)