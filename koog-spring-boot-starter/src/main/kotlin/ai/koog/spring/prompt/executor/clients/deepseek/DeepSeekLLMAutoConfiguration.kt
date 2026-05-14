package ai.koog.spring.prompt.executor.clients.deepseek

import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
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
 * Auto-configuration class for integrating with the DeepSeek LLM provider within a Spring Boot application.
 *
 * This configuration enables the auto-wiring of required beans when the appropriate application
 * properties are set. The configuration ensures that the [DeepSeekLLMClient] is properly initialized
 * and available for usage in the application.
 *
 * The following conditions must be met for this configuration to be activated:
 * - The property `ai.koog.deepseek.api-key` must be defined in the application configuration.
 * - The property `ai.koog.deepseek.enabled` must have a value of `true`.
 *
 * Properties used:
 * - `ai.koog.deepseek.api-key`: API key required to authenticate requests to the DeepSeek API.
 * - `ai.koog.deepseek.base-url`: Base URL of the DeepSeek API, with a default value of `https://api.deepseek.com`.
 * - `ai.koog.deepseek.retry`: Retry configuration settings for failed requests.
 *
 * Beans provided:
 * - [DeepSeekLLMClient]: A client for interacting with the DeepSeek API.
 * - [SingleLLMPromptExecutor]: A bean for executing single-step LLM prompts using the DeepSeek client.
 *
 * @property properties [DeepSeekKoogProperties] containing the configuration properties for the DeepSeek client.
 *
 * @see DeepSeekKoogProperties
 * @see DeepSeekLLMClient
 * @see SingleLLMPromptExecutor
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/deepseek-llm.properties")
@EnableConfigurationProperties(
    DeepSeekKoogProperties::class,
)
public class DeepSeekLLMAutoConfiguration(
    private val properties: DeepSeekKoogProperties
) {

    private val logger = LoggerFactory.getLogger(DeepSeekLLMAutoConfiguration::class.java)

    /**
     * Creates a [DeepSeekLLMClient] bean configured with application properties.
     *
     * This method initializes a [DeepSeekLLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.deepseek.api-key` property is defined and `koog.ai.deepseek.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return A [DeepSeekLLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnPropertyNotEmpty(
        prefix = DeepSeekKoogProperties.PREFIX,
        name = "api-key"
    )
    @ConditionalOnProperty(prefix = DeepSeekKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun deepSeekLLMClient(): DeepSeekLLMClient {
        logger.info("Creating DeepSeekLLMClient with baseUrl=${properties.baseUrl}")
        return DeepSeekLLMClient(
            apiKey = properties.apiKey,
            settings = DeepSeekClientSettings(baseUrl = properties.baseUrl)
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
    @ConditionalOnBean(DeepSeekLLMClient::class)
    public fun deepSeekExecutor(client: DeepSeekLLMClient): SingleLLMPromptExecutor {
        logger.info("Creating SingleLLMPromptExecutor (deepSeekExecutor) for DeepSeekLLMClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
