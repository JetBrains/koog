package ai.koog.agents.example.funApi

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.extension.asAssistantMessage
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.requestLLMMultiple
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val funcAgent = AIAgent<String, String>(
        systemPrompt = "You're helpful librarian agent.",
        promptExecutor = simpleOllamaAIExecutor(),
        strategy = functionalStrategy {
            val responses = requestLLMMultiple(it)

            // Result:
            responses.single().asAssistantMessage().content
        },
        llmModel = OllamaModels.Meta.LLAMA_3_2,
    )

    println(funcAgent.run("Give me a list of top 10 books of all time"))
}
