package ai.koog.spring

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterClientSettings
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(KoogProperties::class)
public class KoogAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["anthropic.api-key"])
    public fun anthropicExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.anthropicClientProperties
        return SingleLLMPromptExecutor(
            AnthropicLLMClient(
                apiKey = props.apiKey,
                settings = AnthropicClientSettings(baseUrl = props.baseUrl)
            )
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["google.api-key"])
    public fun googleExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.googleClientProperties
        return SingleLLMPromptExecutor(
            GoogleLLMClient(
                apiKey = props.apiKey,
                settings = GoogleClientSettings(baseUrl = props.baseUrl)
            )
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["ollama"])
    public fun ollamaExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.ollamaClientProperties
        return SingleLLMPromptExecutor(
            OllamaClient(baseUrl = props.baseUrl)
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["openai.api-key"])
    public fun openAIExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.openAIClientProperties
        return SingleLLMPromptExecutor(
            OpenAILLMClient(
                apiKey = props.apiKey,
                settings = OpenAIClientSettings(baseUrl = props.baseUrl)
            )
        )
    }

    @Bean
    @ConditionalOnProperty(prefix = KoogProperties.PREFIX, name = ["openrouter.api-key"])
    public fun openRouterExecutor(properties: KoogProperties): SingleLLMPromptExecutor {
        val props = properties.openRouterClientProperties
        return SingleLLMPromptExecutor(
            OpenRouterLLMClient(
                props.apiKey,
                settings = OpenRouterClientSettings(baseUrl = props.baseUrl)
            )
        )
    }
}