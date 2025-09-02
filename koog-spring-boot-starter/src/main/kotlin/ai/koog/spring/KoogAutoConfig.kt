package ai.koog.spring

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * [KoogAutoConfiguration] is a Spring Boot auto-configuration class that configures and provides beans
 * for various LLM (Large Language Model) provider clients. It ensures that the beans are only
 * created if the corresponding properties are defined in the application's configuration.
 *
 * This configuration includes support for Anthropic, Google, Ollama, OpenAI, DeepSeek, and OpenRouter providers.
 * Each provider is configured with specific settings and logic encapsulated within a
 * [SingleLLMPromptExecutor] instance backed by a respective client implementation.
 */
@AutoConfiguration
@EnableConfigurationProperties(
    OpenAIKoogProperties::class,
    AnthropicKoogProperties::class,
    GoogleKoogProperties::class,
    OllamaKoogProperties::class,
    DeepSeekKoogProperties::class,
    OpenRouterKoogProperties::class
)
public class KoogAutoConfiguration {

    /**
     * Creates and configures a [SingleLLMPromptExecutor] using an [AnthropicLLMClient].
     * This is conditioned on the presence of an API key in the application properties.
     *
     * @param properties The configuration properties containing settings for the Anthropic client.
     * @return An instance of [SingleLLMPromptExecutor] configured with [AnthropicLLMClient].
     */
    @Bean
    @ConditionalOnProperty(prefix = AnthropicKoogProperties.PREFIX, name = ["api-key"])
    public fun anthropicExecutor(properties: AnthropicKoogProperties): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(
            AnthropicLLMClient(
                apiKey = properties.apiKey,
                settings = AnthropicClientSettings(baseUrl = properties.baseUrl)
            )
        )
    }

    /**
     * Provides a [SingleLLMPromptExecutor] bean configured with a [GoogleLLMClient] using the settings
     * from the given `KoogProperties`. The bean is only created if the `google.api-key` property is set.
     *
     * @param properties The configuration properties containing the `googleClientProperties` needed to create the client.
     * @return A [SingleLLMPromptExecutor] instance configured with a [GoogleLLMClient].
     */
    @Bean
    @ConditionalOnProperty(prefix = GoogleKoogProperties.PREFIX, name = ["api-key"])
    public fun googleExecutor(properties: GoogleKoogProperties): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(
            GoogleLLMClient(
                apiKey = properties.apiKey,
                settings = GoogleClientSettings(baseUrl = properties.baseUrl)
            )
        )
    }

    /**
     * Creates and configures a [SingleLLMPromptExecutor] instance using Ollama properties.
     *
     * The method initializes an [OllamaClient] with the base URL derived from the provided [OllamaKoogProperties]
     * and uses it to construct the [SingleLLMPromptExecutor].
     *
     * @param properties the configuration properties containing Ollama client settings such as the base URL.
     * @return a [SingleLLMPromptExecutor] configured to use the Ollama client.
     */
    @Bean
    @ConditionalOnProperty(prefix = OllamaKoogProperties.PREFIX, name = ["base-url"])
    public fun ollamaExecutor(properties: OllamaKoogProperties): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(
            OllamaClient(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Provides a bean of type [SingleLLMPromptExecutor] configured for OpenAI interaction.
     * The bean will only be instantiated if the property `ai.koog.openai.api-key` is defined in the application properties.
     *
     * @param properties The configuration properties containing OpenAI-specific client settings such as API key and base URL.
     * @return An instance of [SingleLLMPromptExecutor] initialized with the OpenAI client.
     */
    @Bean
    @ConditionalOnProperty(prefix = OpenAIKoogProperties.PREFIX, name = ["api-key"])
    public fun openAIExecutor(properties: OpenAIKoogProperties): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = properties.apiKey,
                settings = OpenAIClientSettings(baseUrl = properties.baseUrl)
            )
        )
    }

    /**
     * Creates a [SingleLLMPromptExecutor] bean configured to use the OpenRouter LLM client.
     *
     * This method is only executed if the `openrouter.api-key` property is defined in the application's configuration.
     * It initializes the OpenRouter client using the provided API key and base URL from the application's properties.
     *
     * @param properties The configuration properties for the application, including the OpenRouter client settings.
     * @return A [SingleLLMPromptExecutor] initialized with an OpenRouter LLM client.
     */
    @Bean
    @ConditionalOnProperty(prefix = OpenRouterKoogProperties.PREFIX, name = ["api-key"])
    public fun openRouterExecutor(properties: OpenRouterKoogProperties): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(
            OpenRouterLLMClient(
                properties.apiKey,
                settings = OpenRouterClientSettings(baseUrl = properties.baseUrl)
            )
        )
    }

    /**
     * Creates a [SingleLLMPromptExecutor] bean configured to use the DeepSeek LLM client.
     *
     * This method is only executed if the `deepseek.api-key` property is defined in the application's configuration.
     * It initializes the DeepSeek client using the provided API key and base URL from the application's properties.
     *
     * @param properties The configuration properties for the application, including the DeepSeek client settings.
     * @return A [SingleLLMPromptExecutor] initialized with an DeepSeek LLM client.
     */
    @Bean
    @ConditionalOnProperty(prefix = DeepSeekKoogProperties.PREFIX, name = ["api-key"])
    public fun deepSeekExecutor(properties: DeepSeekKoogProperties): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(
            DeepSeekLLMClient(
                properties.apiKey,
                settings = DeepSeekClientSettings(baseUrl = properties.baseUrl)
            )
        )
    }
}
