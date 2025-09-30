package ai.koog.spring.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

/**
 * Auto-configuration class for integrating OpenRouter with Koog framework.
 *
 * This class enables the automatic configuration of beans and properties to work with OpenRouter's LLM services,
 * provided the application properties have been set with the required prefix and fields.
 *
 * The configuration is activated only when both `ai.koog.openrouter.enabled` is set to `true`
 * and `ai.koog.openrouter.api-key` is provided in the application properties.
 *
 * @property properties [OpenRouterKoogProperties] to define key settings such as API key, base URL, and retry configurations.
 * @see OpenRouterKoogProperties
 * @see OpenRouterLLMClient
 * @see SingleLLMPromptExecutor
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
     * Creates and configures an instance of [OpenRouterLLMClient] as a Spring Bean.
     * The client is initialized with the API key and settings (such as base URL)
     * obtained from the provided `properties` configuration.
     *
     * @return An instance of [OpenRouterLLMClient] configured with the given properties.
     */
    @Bean
    public fun openRouterLLMClient(): OpenRouterLLMClient {
        return OpenRouterLLMClient(
            apiKey = properties.apiKey,
            settings = OpenRouterClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Provides a [SingleLLMPromptExecutor] bean configured with an [OpenRouterLLMClient].
     *
     * The method uses the provided [OpenRouterLLMClient] to create a retrying client instance
     * based on the configuration in the `properties.retry` parameter.
     *
     * @param client The [OpenRouterLLMClient] instance used to configure the [SingleLLMPromptExecutor]
     * */
    @Bean
    @ConditionalOnBean(OpenRouterLLMClient::class)
    public fun openRouterExecutor(client: OpenRouterLLMClient): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
