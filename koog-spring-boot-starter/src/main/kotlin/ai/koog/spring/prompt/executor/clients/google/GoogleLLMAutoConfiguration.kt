package ai.koog.spring.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.spring.prompt.executor.clients.toRetryingClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.PropertySource

/**
 * Provides the auto-configuration for integrating with Google LLM via the Koog framework.
 * This class is responsible for initializing and configuring the necessary beans for interacting
 * with Google's APIs, based on the configurations supplied via `GoogleKoogProperties`.
 *
 * The configuration is activated only when the property `ai.koog.google.enabled` is set to `true`,
 * and an `api-key` is provided.
 *
 * Beans configured by this class:
 * - `GoogleLLMClient`: A client for interacting with Google's LLM API, using the specified API key and settings.
 * - `SingleLLMPromptExecutor`: An executor capable of handling and retrying LLM prompts, using the initialized client.
 *
 * Dependencies:
 * - Requires the presence of properties defined in `GoogleKoogProperties` to function correctly.
 * - Relies on `KoogLlmClientProperties` for common client settings.
 *
 * An external configuration file at `classpath:/META-INF/config/koog/google-llm.properties` is leveraged
 * for managing default settings.
 */
@AutoConfiguration
@PropertySource("classpath:/META-INF/config/koog/google-llm.properties")
@EnableConfigurationProperties(
    GoogleKoogProperties::class,
)
@ConditionalOnProperty(prefix = GoogleKoogProperties.PREFIX, name = ["api-key"])
@ConditionalOnProperty(prefix = GoogleKoogProperties.PREFIX, name = ["enabled"], havingValue = "true")
public class GoogleLLMAutoConfiguration(
    private val properties: GoogleKoogProperties
) {

    @Bean
    public fun googleLLMClient(): GoogleLLMClient {
        return GoogleLLMClient(
            apiKey = properties.apiKey,
            settings = GoogleClientSettings(baseUrl = properties.baseUrl)
        )
    }

    /**
     * Provides a [SingleLLMPromptExecutor] bean configured with a [GoogleLLMClient] using the settings
     * from the given `KoogProperties`. The bean is only created if the `google.api-key` property is set.
     *
     * @param properties The configuration properties containing the `googleClientProperties` needed to create the client.
     * @return A [SingleLLMPromptExecutor] instance configured with a [GoogleLLMClient].
     */
    @Bean
    public fun googleExecutor(client: GoogleLLMClient): SingleLLMPromptExecutor {
        return SingleLLMPromptExecutor(client.toRetryingClient(properties.retry))
    }
}
