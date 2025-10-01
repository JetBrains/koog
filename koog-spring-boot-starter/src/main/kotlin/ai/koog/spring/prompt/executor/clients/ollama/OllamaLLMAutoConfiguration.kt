package ai.koog.spring.prompt.executor.clients.ollama

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

/**
 * Auto-configuration class for integrating the Ollama Large Language Model (LLM) service into applications.
 *
 * This configuration initializes and provides the necessary beans to enable interaction with the Ollama LLM API.
 * It relies on properties defined in the [OllamaKoogProperties] class to set up the service.
 *
 * The configuration is conditional and will only be initialized if:
 * - [OllamaKoogProperties.enabled] is set to `true`.
 * - The required [OllamaKoogProperties] are provided in the application configuration.
 *
 * Initializes the following beans:
 * - [OllamaClient]: A client for interacting with the Ollama LLM service.
 * - [SingleLLMPromptExecutor]: Executes single-prompt interactions with Ollama, utilizing the client.
 *
 * This configuration allows seamless integration with the Ollama API while enabling properties-based customization.
 *
 * @property properties [OllamaKoogProperties] to define key settings such as API key, base URL, and retry configurations.
 * @see OllamaKoogProperties
 * @see OllamaClient
 * @see SingleLLMPromptExecutor
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/ollama-llm.properties")
@EnableConfigurationProperties(
    OllamaKoogProperties::class,
)
public class OllamaLLMAutoConfiguration(
    private val properties: OllamaKoogProperties
) {

    private val logger = LoggerFactory.getLogger(OllamaLLMAutoConfiguration::class.java)

    /**
     * Creates an [OllamaClient] bean configured with application properties.
     *
     * This method initializes a [OllamaClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.ollama.enabled` property is set to `true` in the application configuration.
     *
     * @return An [OllamaClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnProperty(prefix = OllamaKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun ollamaLLMClient(): OllamaClient {
        logger.info("Creating OllamaClient with baseUrl=${properties.baseUrl}")
        return OllamaClient(
            baseUrl = properties.baseUrl,
        )
    }

    /**
     * Creates and configures an instance of [SingleLLMPromptExecutor] that wraps the provided [OllamaClient].
     * The configured executor includes retry capabilities based on the application's properties.
     *
     * @param client the [OllamaClient] instance used for communicating with the Ollama LLM service.
     * @return a [SingleLLMPromptExecutor] configured to execute LLM prompts with the provided client.
     */
    @Bean
    @ConditionalOnBean(OllamaClient::class)
    public fun ollamaExecutor(client: OllamaClient): SingleLLMPromptExecutor {
        logger.info("Creating SingleLLMPromptExecutor (ollamaExecutor) for OllamaClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
