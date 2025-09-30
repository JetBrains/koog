package ai.koog.spring.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

/**
 * Autoconfiguration class for integrating the OpenRouter LLM (Large Language Model) client within a Spring application.
 * This class is responsible for creating and configuring the necessary beans to interact with the OpenRouter API.
 *
 * The configuration is activated when the corresponding properties in the application's configuration file are set:
 * - The API key must be defined using the prefix specified in [OpenRouterKoogProperties.PREFIX].
 * - The integration must be explicitly enabled by setting the `enabled` property to `true`.
 *
 * The `OpenRouterLLMAutoConfiguration` class relies on the provided [OpenRouterKoogProperties] for configuration,
 * which includes essential parameters such as the API key, base URL, and retry settings.
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/openrouter-llm.properties")
@EnableConfigurationProperties(
    OpenRouterKoogProperties::class,
)
@ConditionalOnProperty(prefix = OpenRouterKoogProperties.PREFIX, name = ["api-key"])
@ConditionalOnProperty(prefix = OpenRouterKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
public class OpenRouterLLMAutoConfiguration(
    private val properties: OpenRouterKoogProperties
) {

    /**
     * Creates and configures an instance of `OpenRouterLLMClient` as a Spring Bean.
     * The client is initialized with the API key and settings (such as base URL)
     * obtained from the provided `properties` configuration.
     *
     * @return An instance of `OpenRouterLLMClient` configured with the given properties.
     */
    @Bean
    public fun openRouterLLMClient(): OpenRouterLLMClient {
        return OpenRouterLLMClient(
            apiKey = properties.apiKey,
            settings = OpenRouterClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Provides a `SingleLLMPromptExecutor` bean configured with an `OpenRouterLLMClient`.
     * The method utilizes the provided `OpenRouterLLMClient` to create a retrying client instance
     * based on the configuration in the `properties.retry` parameter.
     *
     * @param client The `OpenRouterLLMClient` instance used to configure the `Single*/
    @Bean
    public fun openRouterExecutor(client: OpenRouterLLMClient): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
