package ai.koog.agents.example.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.requestLLM
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val funcAgent = AIAgent<String, Unit>(
        systemPrompt = "You're a simple chat agent",
        promptExecutor = simpleOllamaAIExecutor(),
        llmModel = OllamaModels.Meta.LLAMA_3_2
    ) {
        var userResponse = it
        while (userResponse != "/bye") {
            val responses = requestLLM(userResponse)
            println(responses.content)
            userResponse = readln()
        }
    }

    println("Simple chat agent started\nUse /bye to quit\nEnter your message:")
    val input = readln()
    funcAgent.run(input)
}
