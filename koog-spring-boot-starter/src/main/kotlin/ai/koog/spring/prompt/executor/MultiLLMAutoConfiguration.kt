package ai.koog.spring.prompt.executor

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration
public class MultiLLMAutoConfiguration {

    @Bean
    public fun multiLLMPromptExecutor(@Autowired llmClients: List<LLMClient>): MultiLLMPromptExecutor {
        return MultiLLMPromptExecutor(llmClients = llmClients.toTypedArray())
    }
}
