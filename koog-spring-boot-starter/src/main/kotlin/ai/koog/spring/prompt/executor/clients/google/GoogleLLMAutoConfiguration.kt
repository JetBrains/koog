package ai.koog.spring.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.conditions.ConditionalOnPropertyNotEmpty
import ai.koog.spring.prompt.executor.clients.ollama.OllamaKoogProperties
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

/**
 * Provides the auto-configuration for integrating with Google LLM via the Koog framework.
 * This class is responsible for initializing and configuring the necessary beans for interacting
 * with Google's APIs, based on the configurations supplied via `GoogleKoogProperties`.
 *
 * The configuration is activated only when the property `ai.koog.google.enabled` is set to `true`,
 * and an `api-key` is provided.
 *
 * Beans configured by this class:
 * - [GoogleLLMClient]: A client for interacting with Google's LLM API, using the specified API key and settings.
 * - [SingleLLMPromptExecutor]: An executor capable of handling and retrying LLM prompts, using the initialized client.
 *
 * An external configuration file at `classpath:/META-INF/config/koog/google-llm.properties` is leveraged
 * for managing default settings.
 *
 * @property properties [GoogleKoogProperties] to define key settings such as API key, base URL, and retry configurations.
 *
 * @see GoogleKoogProperties
 * @see GoogleLLMClient
 * @see SingleLLMPromptExecutor
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/google-llm.properties")
@EnableConfigurationProperties(
    GoogleKoogProperties::class,
)
public class GoogleLLMAutoConfiguration(
    private val properties: GoogleKoogProperties
) {

    private val logger = LoggerFactory.getLogger(GoogleLLMAutoConfiguration::class.java)

    /**
     * Creates a [GoogleLLMClient] bean configured with application properties.
     *
     * This method initializes a [GoogleLLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.google.api-key` property is defined and `koog.ai.google.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return A [GoogleLLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnPropertyNotEmpty(
        prefix = GoogleKoogProperties.PREFIX,
        name = "api-key"
    )
    @ConditionalOnProperty(prefix = GoogleKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun googleLLMClient(): GoogleLLMClient {
        logger.info("Creating GoogleLLMClient with baseUrl=${properties.baseUrl}")
        return GoogleLLMClient(
            apiKey = properties.apiKey,
            settings = GoogleClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Creates and configures a [SingleLLMPromptExecutor] instance using [GoogleLLMClient] properties.
     *
     * The method initializes an [GoogleLLMClient] with the base URL derived from the provided [OllamaKoogProperties]
     * and uses it to construct the [SingleLLMPromptExecutor].
     *
     * @param properties the configuration properties containing Ollama client settings such as the base URL.
     * @return a [SingleLLMPromptExecutor] configured to use the Ollama client.
     */
    @Bean
    @ConditionalOnBean(GoogleLLMClient::class)
    public fun googleExecutor(client: GoogleLLMClient): SingleLLMPromptExecutor {
        logger.info("Creating SingleLLMPromptExecutor (googleExecutor) for GoogleLLMClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
