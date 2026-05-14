package ai.koog.spring.prompt.executor.clients.mistralai

import ai.koog.prompt.executor.clients.mistralai.MistralAIClientSettings
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
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
 * Auto-configuration class for setting up MistralAI LLM client and related beans.
 * This class utilizes the properties defined in [MistralAIKoogProperties] to configure and initialize MistralAI-related components,
 * including the client and executor.
 *
 * The configuration is conditionally applied if the property `ai.koog.mistral.api-key` is set to `true`.
 * It reads additional configuration from the properties file located at `classpath:/META-INF/config/koog/mistral-llm.properties`.
 *
 * Key Features:
 * - Sets up the [MistralAILLMClient] bean with API key and base URL from the provided properties.
 * - Configures a [SingleLLMPromptExecutor] bean using the configured MistralAI client with retry capabilities.
 *
 * Usage Notes:
 * - To activate, ensure the `ai.koog.mistral.api-key` property is defined in your application configuration.
 * - Customize behavior and settings using the `ai.koog.mistral.*` configuration properties.
 *
 * @property properties [MistralAIKoogProperties] to define key settings such as API key, base URL, and retry configurations.
 *
 * @see MistralAIKoogProperties
 * @see MistralAILLMClient
 * @see SingleLLMPromptExecutor
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/mistral-llm.properties")
@EnableConfigurationProperties(
    MistralAIKoogProperties::class,
)
public class MistralAILLMAutoConfiguration(
    private val properties: MistralAIKoogProperties
) {

    private val logger = LoggerFactory.getLogger(MistralAILLMAutoConfiguration::class.java)

    /**
     * Creates an [MistralAILLMClient] bean configured with application properties.
     *
     * This method initializes a [MistralAILLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.mistral.api-key` property is defined and `koog.ai.mistral.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return An [MistralAILLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnPropertyNotEmpty(
        prefix = MistralAIKoogProperties.PREFIX,
        name = "api-key"
    )
    @ConditionalOnProperty(prefix = MistralAIKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun mistralAILLMClient(): MistralAILLMClient {
        logger.info("Creating mistralAILLMClient client with baseUrl=${properties.baseUrl}")
        return MistralAILLMClient(
            apiKey = properties.apiKey,
            settings = MistralAIClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Creates and returns a [SingleLLMPromptExecutor] bean configured with the given [MistralAILLMClient].
     * This bean is conditionally initialized only when an [MistralAILLMClient] bean is present in the application context.
     *
     * @param client the [MistralAILLMClient] used to create a retry-capable client for executing LLM prompts.
     * @return a configured instance of [SingleLLMPromptExecutor].
     */
    @Bean
    @ConditionalOnBean(MistralAILLMClient::class)
    public fun mistralAIExecutor(client: MistralAILLMClient): SingleLLMPromptExecutor {
        logger.info("Creating SingleLLMPromptExecutor (MistralAIExecutor) for MistralAILLMClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
