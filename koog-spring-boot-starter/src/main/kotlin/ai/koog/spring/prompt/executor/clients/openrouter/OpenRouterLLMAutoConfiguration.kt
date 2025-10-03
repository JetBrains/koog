package ai.koog.spring.prompt.executor.clients.openrouter

import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
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
public class OpenRouterLLMAutoConfiguration(
    private val properties: OpenRouterKoogProperties
) {

    private val logger = LoggerFactory.getLogger(OpenRouterLLMAutoConfiguration::class.java)

    /**
     * Creates an [OpenRouterLLMClient] bean configured with application properties.
     *
     * This method initializes a [OpenRouterLLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.openrouter.api-key` property is defined and `koog.ai.openrouter.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return An [OpenRouterLLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnPropertyNotEmpty(
        prefix = OpenRouterKoogProperties.PREFIX,
        name = "api-key"
    )
    @ConditionalOnProperty(prefix = OpenRouterKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun openRouterLLMClient(): OpenRouterLLMClient {
        logger.info("Creating OpenRouterLLMClient with baseUrl=${properties.baseUrl}")
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
        logger.info("Creating SingleLLMPromptExecutor (openRouterExecutor) for OpenRouterLLMClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
