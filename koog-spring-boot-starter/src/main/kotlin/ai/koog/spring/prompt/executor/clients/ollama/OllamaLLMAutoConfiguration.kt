package ai.koog.spring.prompt.executor.clients.ollama

import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.spring.prompt.executor.clients.toRetryingClient
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
@ConditionalOnProperty(prefix = OllamaKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
public class OllamaLLMAutoConfiguration(
    private val properties: OllamaKoogProperties
) {

    /**
     * Creates and configures an instance of [OllamaClient] using the base URL from the provided properties.
     *
     * This client is used to communicate with the Ollama LLM service and is a prerequisite
     * for executing prompts and other interactions with the service.
     *
     * @return an [OllamaClient] configured with the base URL extracted from the application's properties.
     */
    @Bean
    public fun ollamaLLMClient(): OllamaClient {
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
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
