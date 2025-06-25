package ai.koog.spring

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = KoogProperties.PREFIX)
public class KoogProperties {
    public companion object {
        public const val PREFIX: String = "ai.koog"
    }

    @NestedConfigurationProperty
    public var anthropicClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://api.anthropic.com")

    @NestedConfigurationProperty
    public var googleClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://generativelanguage.googleapis.com")

    @NestedConfigurationProperty
    public var ollamaClientProperties: OllamaKoogProperties = OllamaKoogProperties()

    @NestedConfigurationProperty
    public var openAIClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://api.openai.com")

    @NestedConfigurationProperty
    public var openRouterClientProperties: ProviderKoogProperties =
        ProviderKoogProperties(baseUrl = "https://openrouter.ai")
}

public data class ProviderKoogProperties(
    val apiKey: String = "",
    val baseUrl: String
)

public data class OllamaKoogProperties(
    val baseUrl: String = "http://localhost:11434"
)
