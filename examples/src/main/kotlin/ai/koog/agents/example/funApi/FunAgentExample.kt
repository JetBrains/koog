package ai.koog.agents.example.funApi

import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.functionalAIAgent
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val funcAgent = functionalAIAgent<String, String>(
        prompt = "You're helpful librarian agent.",
        promptExecutor = simpleOllamaAIExecutor(),
        model = OllamaModels.Meta.LLAMA_3_2,
    ) {
        val responses = requestLLMMultiple(it)
        return@functionalAIAgent responses.single().asAssistantMessage().content
    }

    println(funcAgent.run("Give me a list of top 10 books of all time"))
}
