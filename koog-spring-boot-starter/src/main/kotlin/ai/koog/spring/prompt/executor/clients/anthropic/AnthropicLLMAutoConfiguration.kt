package ai.koog.spring.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

/**
 * Auto-configuration class for Anthropic LLM integration in a Spring Boot application.
 *
 * This class automatically configures the required beans for interacting with the Anthropic LLM
 * when the appropriate configuration properties are set in the application. It specifically checks
 * for the presence of the `ai.koog.anthropic.enabled` and `ai.koog.anthropic.api-key` properties.
 *
 * Beans provided by this configuration:
 * - [AnthropicLLMClient]: Configured client for interacting with the Anthropic API.
 * - [SingleLLMPromptExecutor]: Prompt executor that utilizes the configured Anthropic client.
 *
 * To enable this configuration, the `ai.koog.anthropic.enabled` property must be set to `true` and a valid `api-key`
 * must be provided in the application's property files.
 *
 * This configuration reads additional properties from the `classpath:META-INF/config/koog/anthropic-llm.properties`
 * and binds them to the [AnthropicKoogProperties].
 *
 * @property properties Anthropic-specific configuration properties, automatically injected by Spring's
 *                      configuration properties mechanism.
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

    /**
     * Creates and initializes an instance of [AnthropicLLMClient] with the specified API key and settings from the
     * application properties. The client is configured to interact with the Anthropic LLM API using the provided
     * base URL and credentials.
     *
     * @return An instance of [AnthropicLLMClient] configured for communication with the Anthropic API.
     */
    @Bean
    public fun anthropicLLMClient(): AnthropicLLMClient {
        logger.info("Initializing AnthropicLLMClient with: $properties")
        return AnthropicLLMClient(
            apiKey = properties.apiKey,
            settings = AnthropicClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Creates and initializes a [SingleLLMPromptExecutor] instance using an [AnthropicLLMClient].
     * The executor is configured with a retrying client derived from the provided AnthropicLLMClient.
     *
     * @param client An instance of [AnthropicLLMClient] used to communicate with the Anthropic LLM API.
     * @return An instance of [SingleLLMPromptExecutor] for sending prompts to the Anthropic LLM API.
     */
    @Bean
    @ConditionalOnBean(AnthropicLLMClient::class)
    public fun anthropicExecutor(client: AnthropicLLMClient): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
