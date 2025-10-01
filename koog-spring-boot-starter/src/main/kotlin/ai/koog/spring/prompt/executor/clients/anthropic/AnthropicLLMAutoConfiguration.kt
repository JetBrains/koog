package ai.koog.spring.prompt.executor.clients.anthropic

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.conditions.ConditionalOnPropertyNotEmpty
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
 * To enable this configuration, the `ai.koog.anthropic.enabled` property must be set to `true`
 * and a valid `ai.koog.anthropic.api-key` must be provided in the application's property files.
 *
 * This configuration reads additional properties imported via `spring.config.import` from the starter's
 * application.properties file and binds them to the [AnthropicKoogProperties].
 *
 * @property properties Anthropic-specific configuration properties, automatically injected by Spring's
 *                      configuration properties mechanism.
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/anthropic-llm.properties")
@EnableConfigurationProperties(
    AnthropicKoogProperties::class,
)
public class AnthropicLLMAutoConfiguration(
    private val properties: AnthropicKoogProperties
) {

    private val logger = LoggerFactory.getLogger(AnthropicLLMAutoConfiguration::class.java)

    /**
     * Creates an [AnthropicLLMClient] bean configured with application properties.
     *
     * This method initializes a [AnthropicLLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.anthropic.api-key` property is defined and `koog.ai.anthropic.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return An [AnthropicLLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnProperty(prefix = AnthropicKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    @ConditionalOnPropertyNotEmpty(
        prefix = AnthropicKoogProperties.PREFIX,
        name = "api-key"
    )
    public fun anthropicLLMClient(): AnthropicLLMClient {
        logger.info("Creating AnthropicLLMClient with baseUrl=${properties.baseUrl}")
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
        logger.info("Creating SingleLLMPromptExecutor (anthropicExecutor) for AnthropicLLMClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
