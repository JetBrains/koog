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
 * It relies on properties defined in the `OllamaKoogProperties` class to set up the service.
 *
 * The configuration is conditional and will only be initialized if:
 * - The `enabled` property within `OllamaKoogProperties` is set to `true`.
 * - The required `OllamaKoogProperties` are provided in the application configuration.
 *
 * Initializes the following beans:
 * - `OllamaClient`: A client for interacting with the Ollama LLM service.
 * - `SingleLLMPromptExecutor`: Executes single-prompt interactions with Ollama, utilizing the client.
 *
 * This configuration allows seamless integration with the Ollama API while enabling properties-based customization.
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
     * Creates and configures a [SingleLLMPromptExecutor] instance using Ollama properties.
     *
     * The method initializes an [OllamaClient] with the base URL derived from the provided [OllamaKoogProperties]
     * and uses it to construct the [SingleLLMPromptExecutor].
     *
     * @param properties the configuration properties containing Ollama client settings such as the base URL.
     * @return a [SingleLLMPromptExecutor] configured to use the Ollama client.
     */
    @Bean
    @ConditionalOnBean(OllamaClient::class)
    public fun ollamaExecutor(client: OllamaClient): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
