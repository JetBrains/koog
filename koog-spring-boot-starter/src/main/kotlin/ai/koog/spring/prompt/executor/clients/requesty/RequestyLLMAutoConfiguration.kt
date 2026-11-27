package ai.koog.spring.prompt.executor.clients.requesty

import ai.koog.prompt.executor.clients.requesty.RequestyClientSettings
import ai.koog.prompt.executor.clients.requesty.RequestyLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
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
 * Auto-configuration class for integrating Requesty with Koog framework.
 *
 * This class enables the automatic configuration of beans and properties to work with Requesty's LLM services,
 * provided the application properties have been set with the required prefix and fields.
 *
 * The configuration is activated only when both `ai.koog.requesty.enabled` is set to `true`
 * and `ai.koog.requesty.api-key` is provided in the application properties.
 *
 * @property properties [RequestyKoogProperties] to define key settings such as API key, base URL, and retry configurations.
 * @see RequestyKoogProperties
 * @see RequestyLLMClient
 * @see SingleLLMPromptExecutor
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/requesty-llm.properties")
@EnableConfigurationProperties(
    RequestyKoogProperties::class,
)
public class RequestyLLMAutoConfiguration(
    private val properties: RequestyKoogProperties
) {

    private val logger = LoggerFactory.getLogger(RequestyLLMAutoConfiguration::class.java)

    /**
     * Creates an [RequestyLLMClient] bean configured with application properties.
     *
     * This method initializes a [RequestyLLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.requesty.api-key` property is defined and `koog.ai.requesty.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return An [RequestyLLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnPropertyNotEmpty(
        prefix = RequestyKoogProperties.PREFIX,
        name = "api-key"
    )
    @ConditionalOnProperty(prefix = RequestyKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun requestyLLMClient(): RequestyLLMClient {
        logger.info("Creating RequestyLLMClient with baseUrl=${properties.baseUrl}")
        return RequestyLLMClient(
            apiKey = properties.apiKey,
            settings = RequestyClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Provides a [SingleLLMPromptExecutor] bean configured with an [RequestyLLMClient].
     *
     * The method uses the provided [RequestyLLMClient] to create a retrying client instance
     * based on the configuration in the `properties.retry` parameter.
     *
     * @param client The [RequestyLLMClient] instance used to configure the [SingleLLMPromptExecutor]
     * */
    @Bean
    @ConditionalOnBean(RequestyLLMClient::class)
    public fun requestyExecutor(client: RequestyLLMClient): PromptExecutor {
        logger.info("Creating MultiLLMPromptExecutor (requestyExecutor) for RequestyLLMClient")
        return MultiLLMPromptExecutor(LLMProvider.Requesty to client.toRetryingClient(properties.retry))
    }
}
