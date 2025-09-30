package ai.koog.spring.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

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
@PropertySource("classpath:/META-INF/config/koog/anthropic-llm.properties")
@EnableConfigurationProperties(
    AnthropicKoogProperties::class,
)
@ConditionalOnProperty(prefix = AnthropicKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
@ConditionalOnProperty(prefix = AnthropicKoogProperties.PREFIX, name = ["api-key"])
public class AnthropicLLMAutoConfiguration(
    private val properties: AnthropicKoogProperties
) {

    private val logger = LoggerFactory.getLogger(AnthropicLLMAutoConfiguration::class.java)

    @Bean
    public fun anthropicLLMClient(): AnthropicLLMClient {
        logger.info("Initializing AnthropicLLMClient with: $properties")
        return AnthropicLLMClient(
            apiKey = properties.apiKey,
            settings = AnthropicClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Creates and configures a [SingleLLMPromptExecutor] using an [AnthropicLLMClient].
     * This is conditioned on the presence of an API key in the application properties.
     *
     * @param properties The configuration properties containing settings for the Anthropic client.
     * @return An instance of [SingleLLMPromptExecutor] configured with [AnthropicLLMClient].
     */
    @Bean
    public fun anthropicExecutor(client: AnthropicLLMClient): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
