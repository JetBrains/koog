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
 * Auto-configuration class for integrating the Ollama LLM provider with the application.
 *
 * This configuration class initializes necessary beans and ensures proper setup for using the
 * Ollama LLM provider. It activates when the `ai.koog.ollama.api-key` property is defined in the
 * application configuration. It relies on properties defined in [OllamaKoogProperties] and provides
 * the necessary clients for interfacing with the provider.
 *
 * The configuration:
 * - Reads properties from `ollama-llm.properties` located in the META-INF directory.
 * - Registers beans for an Ollama client and a prompt executor.
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
