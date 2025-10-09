package ai.koog.spring.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
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
 * Auto-configuration class for setting up OpenAI LLM client and related beans.
 * This class utilizes the properties defined in [OpenAIKoogProperties] to configure and initialize OpenAI-related components,
 * including the client and executor.
 *
 * The configuration is conditionally applied if the property `ai.koog.openai.api-key` is set to `true`.
 * It reads additional configuration from the properties file located at `classpath:/META-INF/config/koog/openai-llm.properties`.
 *
 * Key Features:
 * - Sets up the [OpenAILLMClient] bean with API key and base URL from the provided properties.
 * - Configures a [SingleLLMPromptExecutor] bean using the configured OpenAI client with retry capabilities.
 *
 * Usage Notes:
 * - To activate, ensure the `ai.koog.openai.api-key` property is defined in your application configuration.
 * - Customize behavior and settings using the `ai.koog.openai.*` configuration properties.
 *
 * @property properties [OpenAIKoogProperties] to define key settings such as API key, base URL, and retry configurations.
 *
 * @see OpenAIKoogProperties
 * @see OpenAILLMClient
 * @see SingleLLMPromptExecutor
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/openai-llm.properties")
@EnableConfigurationProperties(
    OpenAIKoogProperties::class,
)
public class OpenAILLMAutoConfiguration(
    private val properties: OpenAIKoogProperties
) {

    private val logger = LoggerFactory.getLogger(OpenAILLMAutoConfiguration::class.java)

    /**
     * Creates an [OpenAILLMClient] bean configured with application properties.
     *
     * This method initializes a [OpenAILLMClient] using the API key and base URL
     * specified in the application's configuration. It is only executed if the
     * `koog.ai.openai.api-key` property is defined and `koog.ai.openai.enabled` property is set
     * to `true` in the application configuration.
     *
     * @return An [OpenAILLMClient] instance configured with the provided settings.
     */
    @Bean
    @ConditionalOnPropertyNotEmpty(
        prefix = OpenAIKoogProperties.PREFIX,
        name = "api-key"
    )
    @ConditionalOnProperty(prefix = OpenAIKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
    public fun openAILLMClient(): OpenAILLMClient {
        logger.info("Creating OpenAILLMClient client with baseUrl=${properties.baseUrl}")
        return OpenAILLMClient(
            apiKey = properties.apiKey,
            settings = OpenAIClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Creates and returns a [SingleLLMPromptExecutor] bean configured with the given [OpenAILLMClient].
     * This bean is conditionally initialized only when an [OpenAILLMClient] bean is present in the application context.
     *
     * @param client the [OpenAILLMClient] used to create a retry-capable client for executing LLM prompts.
     * @return a configured instance of [SingleLLMPromptExecutor].
     */
    @Bean
    @ConditionalOnBean(OpenAILLMClient::class)
    public fun openAIExecutor(client: OpenAILLMClient): SingleLLMPromptExecutor {
        logger.info("Creating SingleLLMPromptExecutor (openAIExecutor) for OpenAILLMClient")
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
