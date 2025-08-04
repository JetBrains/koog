package ai.koog.spring

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
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
 * This configuration includes support for Anthropic, Google, Ollama, OpenAI, and OpenRouter providers.
 * Each provider is configured with specific settings and logic encapsulated within a
 * [SingleLLMPromptExecutor] instance backed by a respective client implementation.
 */
@AutoConfiguration
@EnableConfigurationProperties(KoogProperties::class)
public class KoogAutoConfiguration {

    /**
     * Creates and configures a [SingleLLMPromptExecutor] using an [AnthropicLLMClient].
     * This is conditioned on the presence of an API key in the application properties.
     *
     * @param properties The configuration properties containing settings for the Anthropic client.
     * @return An instance of [SingleLLMPromptExecutor] configured with [AnthropicLLMClient].
     */
    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["anthropic.api-key"])
    public fun anthropicExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.anthropicClientProperties
        return SingleLLMPromptExecutor(
            AnthropicLLMClient(
                apiKey = props.apiKey,
                settings = AnthropicClientSettings(baseUrl = props.baseUrl)
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
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["google.api-key"])
    public fun googleExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.googleClientProperties
        return SingleLLMPromptExecutor(
            GoogleLLMClient(
                apiKey = props.apiKey,
                settings = GoogleClientSettings(baseUrl = props.baseUrl)
            )
        )
    }

    /**
     * Creates and configures a [SingleLLMPromptExecutor] instance using Ollama properties.
     *
     * The method initializes an [OllamaClient] with the base URL derived from the provided [KoogProperties]
     * and uses it to construct the [SingleLLMPromptExecutor].
     *
     * @param properties the configuration properties containing Ollama client settings such as the base URL.
     * @return a [SingleLLMPromptExecutor] configured to use the Ollama client.
     */
    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["ollama"])
    public fun ollamaExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.ollamaClientProperties
        return SingleLLMPromptExecutor(
            OllamaClient(baseUrl = props.baseUrl)
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
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["openai.api-key"])
    public fun openAIExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.openAIClientProperties
        return SingleLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = props.apiKey,
                settings = OpenAIClientSettings(baseUrl = props.baseUrl)
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
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["openrouter.api-key"])
    public fun openRouterExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.openRouterClientProperties
        return SingleLLMPromptExecutor(
            OpenRouterLLMClient(
                props.apiKey,
                settings = OpenRouterClientSettings(baseUrl = props.baseUrl)
            )
        )
    }
}
