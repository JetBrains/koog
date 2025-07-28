package ai.koog.agents.secure.apikeys

/**
 * Type-safe API key provider system for secure storage.
 * 
 * This provides compile-time safety for API key management by using specific types
 * for each supported provider instead of string-based service names.
 * 
 * Benefits:
 * - Compile-time error detection for typos
 * - IDE autocomplete and refactoring support
 * - Clear documentation of supported providers
 * - Type-safe context scoping per provider
 */
public sealed interface ApiKeyProvider {
    /**
     * Unique identifier for this provider.
     */
    public val serviceId: String
    
    /**
     * Human-readable name of the service.
     */
    public val displayName: String
    
    /**
     * Validation pattern for API keys (optional).
     */
    public val keyPattern: Regex?
        get() = null
}

/**
 * LLM/AI Service Providers
 */
public object OpenAIProvider : ApiKeyProvider {
    override val serviceId: String = "openai"
    override val displayName: String = "OpenAI"
    override val keyPattern: Regex = Regex("^sk-[a-zA-Z0-9]{20,}$")
}

public object AnthropicProvider : ApiKeyProvider {
    override val serviceId: String = "anthropic"
    override val displayName: String = "Anthropic"
    override val keyPattern: Regex = Regex("^sk-ant-[a-zA-Z0-9-_]{20,}$")
}

public object CohereProvider : ApiKeyProvider {
    override val serviceId: String = "cohere"
    override val displayName: String = "Cohere"
    override val keyPattern: Regex = Regex("^[a-zA-Z0-9]{40}$")
}

public object HuggingFaceProvider : ApiKeyProvider {
    override val serviceId: String = "huggingface"
    override val displayName: String = "Hugging Face"
    override val keyPattern: Regex = Regex("^hf_[a-zA-Z0-9]{34}$")
}

public object GoogleAIProvider : ApiKeyProvider {
    override val serviceId: String = "google-ai"
    override val displayName: String = "Google AI"
    override val keyPattern: Regex = Regex("^AIza[a-zA-Z0-9_-]{35}$")
}

/**
 * Cloud Service Providers
 */
public object AWSProvider : ApiKeyProvider {
    override val serviceId: String = "aws"
    override val displayName: String = "Amazon Web Services"
    override val keyPattern: Regex = Regex("^AKIA[0-9A-Z]{16}$")
}

public object GoogleCloudProvider : ApiKeyProvider {
    override val serviceId: String = "google-cloud"
    override val displayName: String = "Google Cloud Platform"
    // GCP uses service account JSON files, not simple API keys
}

public object AzureProvider : ApiKeyProvider {
    override val serviceId: String = "azure"
    override val displayName: String = "Microsoft Azure"
    // Azure uses various key formats depending on service
}

/**
 * Payment & Financial Services
 */
public object StripeProvider : ApiKeyProvider {
    override val serviceId: String = "stripe"
    override val displayName: String = "Stripe"
    override val keyPattern: Regex = Regex("^(sk|pk)_(test|live)_[a-zA-Z0-9]{24,}$")
}

public object PayPalProvider : ApiKeyProvider {
    override val serviceId: String = "paypal"
    override val displayName: String = "PayPal"
    // PayPal uses client ID + secret pairs
}

/**
 * Developer Tools & APIs
 */
public object GitHubProvider : ApiKeyProvider {
    override val serviceId: String = "github"
    override val displayName: String = "GitHub"
    override val keyPattern: Regex = Regex("^gh[pousr]_[a-zA-Z0-9]{36,}$")
}

public object GitLabProvider : ApiKeyProvider {
    override val serviceId: String = "gitlab"
    override val displayName: String = "GitLab"
    override val keyPattern: Regex = Regex("^glpat-[a-zA-Z0-9_-]{20}$")
}

public object SlackProvider : ApiKeyProvider {
    override val serviceId: String = "slack"
    override val displayName: String = "Slack"
    override val keyPattern: Regex = Regex("^xoxb-[0-9]+-[0-9]+-[a-zA-Z0-9]+$")
}

public object DiscordProvider : ApiKeyProvider {
    override val serviceId: String = "discord"
    override val displayName: String = "Discord"
    // Discord bot tokens have specific format
}

/**
 * Custom provider for services not covered by built-in providers.
 */
public data class CustomProvider(
    override val serviceId: String,
    override val displayName: String,
    override val keyPattern: Regex? = null
) : ApiKeyProvider

/**
 * Registry of all built-in providers for easy access.
 */
public object ApiKeyProviders {
    
    // LLM/AI Services
    public val OpenAI: OpenAIProvider = OpenAIProvider
    public val Anthropic: AnthropicProvider = AnthropicProvider
    public val Cohere: CohereProvider = CohereProvider
    public val HuggingFace: HuggingFaceProvider = HuggingFaceProvider
    public val GoogleAI: GoogleAIProvider = GoogleAIProvider
    
    // Cloud Services
    public val AWS: AWSProvider = AWSProvider
    public val GoogleCloud: GoogleCloudProvider = GoogleCloudProvider
    public val Azure: AzureProvider = AzureProvider
    
    // Payment Services
    public val Stripe: StripeProvider = StripeProvider
    public val PayPal: PayPalProvider = PayPalProvider
    
    // Developer Tools
    public val GitHub: GitHubProvider = GitHubProvider
    public val GitLab: GitLabProvider = GitLabProvider
    public val Slack: SlackProvider = SlackProvider
    public val Discord: DiscordProvider = DiscordProvider
    
    /**
     * All built-in providers.
     */
    public val all: List<ApiKeyProvider> = listOf(
        OpenAI, Anthropic, Cohere, HuggingFace, GoogleAI,
        AWS, GoogleCloud, Azure,
        Stripe, PayPal,
        GitHub, GitLab, Slack, Discord
    )
    
    /**
     * Find a provider by service ID.
     */
    public fun findByServiceId(serviceId: String): ApiKeyProvider? {
        return all.find { it.serviceId == serviceId }
    }
    
    /**
     * Create a custom provider for services not covered by built-ins.
     */
    public fun custom(
        serviceId: String,
        displayName: String,
        keyPattern: Regex? = null
    ): CustomProvider = CustomProvider(serviceId, displayName, keyPattern)
}

/**
 * Validates an API key against the provider's expected pattern.
 */
public fun ApiKeyProvider.validateKey(apiKey: String): Boolean {
    return keyPattern?.matches(apiKey) ?: true // No pattern means any key is valid
}

/**
 * Exception thrown when an API key doesn't match the expected pattern for a provider.
 */
public class InvalidApiKeyFormatException(
    public val provider: ApiKeyProvider,
    public val providedKey: String,
    message: String
) : IllegalArgumentException(message)