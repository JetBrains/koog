package ai.koog.agents.secure.apikeys

/**
 * Type-safe extension functions for ApiKeyResolver that provide compile-time safety
 * and clear provider specification for API key resolution.
 */

/**
 * Type-safe API key resolution with hierarchical fallback.
 */
public suspend fun ApiKeyResolver.resolveApiKey(
    provider: ApiKeyProvider,
    userContext: String? = null,
    agentContext: String? = null
): String? {
    return resolveApiKey(provider.serviceId, userContext, agentContext)
}

/**
 * Type-safe API key source identification.
 */
public suspend fun ApiKeyResolver.getApiKeySource(
    provider: ApiKeyProvider,
    userContext: String? = null,
    agentContext: String? = null
): ApiKeySource? {
    return getApiKeySource(provider.serviceId, userContext, agentContext)
}

/**
 * Resolve API keys for multiple providers in batch.
 */
public suspend fun ApiKeyResolver.resolveApiKeys(
    providers: List<ApiKeyProvider>,
    userContext: String? = null,
    agentContext: String? = null
): Map<ApiKeyProvider, String?> {
    return providers.associateWith { provider ->
        resolveApiKey(provider, userContext, agentContext)
    }
}

/**
 * Get only the available (non-null) API keys for multiple providers.
 */
public suspend fun ApiKeyResolver.resolveAvailableApiKeys(
    providers: List<ApiKeyProvider>,
    userContext: String? = null,
    agentContext: String? = null
): Map<ApiKeyProvider, String> {
    return providers.mapNotNull { provider ->
        resolveApiKey(provider, userContext, agentContext)?.let { key ->
            provider to key
        }
    }.toMap()
}

/**
 * Resolve all common LLM provider keys.
 */
public suspend fun ApiKeyResolver.resolveLLMProviderKeys(
    userContext: String? = null,
    agentContext: String? = null
): LLMProviderKeys {
    return LLMProviderKeys(
        openAI = resolveApiKey(ApiKeyProviders.OpenAI, userContext, agentContext),
        anthropic = resolveApiKey(ApiKeyProviders.Anthropic, userContext, agentContext),
        cohere = resolveApiKey(ApiKeyProviders.Cohere, userContext, agentContext),
        huggingFace = resolveApiKey(ApiKeyProviders.HuggingFace, userContext, agentContext),
        googleAI = resolveApiKey(ApiKeyProviders.GoogleAI, userContext, agentContext)
    )
}

/**
 * Resolve all cloud provider keys.
 */
public suspend fun ApiKeyResolver.resolveCloudProviderKeys(
    userContext: String? = null,
    agentContext: String? = null
): CloudProviderKeys {
    return CloudProviderKeys(
        aws = resolveApiKey(ApiKeyProviders.AWS, userContext, agentContext),
        googleCloud = resolveApiKey(ApiKeyProviders.GoogleCloud, userContext, agentContext),
        azure = resolveApiKey(ApiKeyProviders.Azure, userContext, agentContext)
    )
}

/**
 * Type-safe container for LLM provider API keys.
 */
public data class LLMProviderKeys(
    val openAI: String? = null,
    val anthropic: String? = null,
    val cohere: String? = null,
    val huggingFace: String? = null,
    val googleAI: String? = null
) {
    /**
     * Get the first available LLM API key in priority order.
     */
    public fun getFirstAvailable(): String? {
        return openAI ?: anthropic ?: cohere ?: huggingFace ?: googleAI
    }
    
    /**
     * Get all available LLM keys as a map.
     */
    public fun getAvailable(): Map<ApiKeyProvider, String> {
        return buildMap {
            openAI?.let { put(ApiKeyProviders.OpenAI, it) }
            anthropic?.let { put(ApiKeyProviders.Anthropic, it) }
            cohere?.let { put(ApiKeyProviders.Cohere, it) }
            huggingFace?.let { put(ApiKeyProviders.HuggingFace, it) }
            googleAI?.let { put(ApiKeyProviders.GoogleAI, it) }
        }
    }
}

/**
 * Type-safe container for cloud provider API keys.
 */
public data class CloudProviderKeys(
    val aws: String? = null,
    val googleCloud: String? = null,
    val azure: String? = null
) {
    /**
     * Get the first available cloud API key in priority order.
     */
    public fun getFirstAvailable(): String? {
        return aws ?: googleCloud ?: azure
    }
    
    /**
     * Get all available cloud keys as a map.
     */
    public fun getAvailable(): Map<ApiKeyProvider, String> {
        return buildMap {
            aws?.let { put(ApiKeyProviders.AWS, it) }
            googleCloud?.let { put(ApiKeyProviders.GoogleCloud, it) }
            azure?.let { put(ApiKeyProviders.Azure, it) }
        }
    }
}

/**
 * Convenience extensions for specific providers
 */

// OpenAI specific resolver
public suspend fun ApiKeyResolver.resolveOpenAIKey(
    userContext: String? = null,
    agentContext: String? = null
): String? = resolveApiKey(ApiKeyProviders.OpenAI, userContext, agentContext)

// Anthropic specific resolver
public suspend fun ApiKeyResolver.resolveAnthropicKey(
    userContext: String? = null,
    agentContext: String? = null
): String? = resolveApiKey(ApiKeyProviders.Anthropic, userContext, agentContext)

// AWS specific resolver
public suspend fun ApiKeyResolver.resolveAWSKey(
    userContext: String? = null,
    agentContext: String? = null
): String? = resolveApiKey(ApiKeyProviders.AWS, userContext, agentContext)

// Stripe specific resolver
public suspend fun ApiKeyResolver.resolveStripeKey(
    userContext: String? = null,
    agentContext: String? = null
): String? = resolveApiKey(ApiKeyProviders.Stripe, userContext, agentContext)

// GitHub specific resolver
public suspend fun ApiKeyResolver.resolveGitHubKey(
    userContext: String? = null,
    agentContext: String? = null
): String? = resolveApiKey(ApiKeyProviders.GitHub, userContext, agentContext)

/**
 * Context-specific resolver for common use cases.
 */
public class TypeSafeContextScopedApiKeyResolver(
    private val resolver: ApiKeyResolver,
    private val userContext: String? = null,
    private val agentContext: String? = null
) {
    
    public suspend fun resolveApiKey(provider: ApiKeyProvider): String? {
        return resolver.resolveApiKey(provider, userContext, agentContext)
    }
    
    public suspend fun getApiKeySource(provider: ApiKeyProvider): ApiKeySource? {
        return resolver.getApiKeySource(provider, userContext, agentContext)
    }
    
    public suspend fun resolveAvailableApiKeys(
        providers: List<ApiKeyProvider> = ApiKeyProviders.all
    ): Map<ApiKeyProvider, String> {
        return resolver.resolveAvailableApiKeys(providers, userContext, agentContext)
    }
    
    public suspend fun resolveLLMProviderKeys(): LLMProviderKeys {
        return resolver.resolveLLMProviderKeys(userContext, agentContext)
    }
    
    public suspend fun resolveCloudProviderKeys(): CloudProviderKeys {
        return resolver.resolveCloudProviderKeys(userContext, agentContext)
    }
    
    // Convenience methods for specific providers
    public suspend fun resolveOpenAIKey(): String? = resolveApiKey(ApiKeyProviders.OpenAI)
    public suspend fun resolveAnthropicKey(): String? = resolveApiKey(ApiKeyProviders.Anthropic)
    public suspend fun resolveAWSKey(): String? = resolveApiKey(ApiKeyProviders.AWS)
    public suspend fun resolveStripeKey(): String? = resolveApiKey(ApiKeyProviders.Stripe)
    public suspend fun resolveGitHubKey(): String? = resolveApiKey(ApiKeyProviders.GitHub)
}

/**
 * Create a context-scoped resolver for specific user/agent combinations.
 */
public fun ApiKeyResolver.scoped(
    userContext: String? = null,
    agentContext: String? = null
): TypeSafeContextScopedApiKeyResolver {
    return TypeSafeContextScopedApiKeyResolver(this, userContext, agentContext)
}

/**
 * Builder for complex API key resolution scenarios.
 */
public class ApiKeyResolutionBuilder(private val resolver: ApiKeyResolver) {
    private var userContext: String? = null
    private var agentContext: String? = null
    private val requiredProviders = mutableListOf<ApiKeyProvider>()
    private val optionalProviders = mutableListOf<ApiKeyProvider>()
    
    public fun forUser(userContext: String): ApiKeyResolutionBuilder {
        this.userContext = userContext
        return this
    }
    
    public fun forAgent(agentContext: String): ApiKeyResolutionBuilder {
        this.agentContext = agentContext
        return this
    }
    
    public fun require(vararg providers: ApiKeyProvider): ApiKeyResolutionBuilder {
        requiredProviders.addAll(providers)
        return this
    }
    
    public fun optional(vararg providers: ApiKeyProvider): ApiKeyResolutionBuilder {
        optionalProviders.addAll(providers)
        return this
    }
    
    /**
     * Execute the resolution and return results.
     */
    public suspend fun resolve(): ApiKeyResolutionResult {
        val allProviders = requiredProviders + optionalProviders
        val resolved = resolver.resolveAvailableApiKeys(allProviders, userContext, agentContext)
        
        val missing = requiredProviders.filter { it !in resolved }
        
        return ApiKeyResolutionResult(
            resolved = resolved,
            missing = missing,
            isComplete = missing.isEmpty()
        )
    }
}

/**
 * Result of API key resolution with validation.
 */
public data class ApiKeyResolutionResult(
    val resolved: Map<ApiKeyProvider, String>,
    val missing: List<ApiKeyProvider>,
    val isComplete: Boolean
) {
    /**
     * Throw an exception if required keys are missing.
     */
    public fun requireComplete(): ApiKeyResolutionResult {
        if (!isComplete) {
            throw MissingApiKeysException(
                "Missing required API keys for providers: ${missing.map { it.displayName }}"
            )
        }
        return this
    }
}

/**
 * Create an API key resolution builder.
 */
public fun ApiKeyResolver.buildResolution(): ApiKeyResolutionBuilder {
    return ApiKeyResolutionBuilder(this)
}

/**
 * Exception thrown when required API keys are missing.
 */
public class MissingApiKeysException(message: String) : IllegalStateException(message)