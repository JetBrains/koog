package ai.koog.agents.example.nongraph

import ai.koog.agents.core.agent.actAIAgent
import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val actAgent = actAIAgent<String, String>(
        prompt = "You're helpful librarian agent.",
        promptExecutor = simpleOllamaAIExecutor(),
        model = OllamaModels.Meta.LLAMA_3_2,
    ) {
        val responses = requestLLMMultiple(it)
        return@actAIAgent responses.single().asAssistantMessage().content
    }

    println(actAgent.run("Give me a list of top 10 books of all time"))
}
