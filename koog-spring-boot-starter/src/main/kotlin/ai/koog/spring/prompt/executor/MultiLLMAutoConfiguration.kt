package ai.koog.spring.prompt.executor

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.factory.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.spring.prompt.executor.clients.anthropic.AnthropicLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.deepseek.DeepSeekLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.google.GoogleLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.mistralai.MistralAILLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.ollama.OllamaLLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.openai.OpenAILLMAutoConfiguration
import ai.koog.spring.prompt.executor.clients.openrouter.OpenRouterLLMAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean

@AutoConfiguration(
    after = [
        AnthropicLLMAutoConfiguration::class,
        DeepSeekLLMAutoConfiguration::class,
        GoogleLLMAutoConfiguration::class,
        MistralAILLMAutoConfiguration::class,
        OllamaLLMAutoConfiguration::class,
        OpenAILLMAutoConfiguration::class,
        OpenRouterLLMAutoConfiguration::class,
    ],
)
public class MultiLLMAutoConfiguration {

    @Bean
    @ConditionalOnBean(LLMClient::class)
    public fun multiLLMPromptExecutor(@Autowired llmClients: List<LLMClient>): PromptExecutor {
        return MultiLLMPromptExecutor(llmClients = llmClients.toTypedArray())
    }
}
