package ai.koog.agents.example.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.
import ai.koog.agents.core.dsl.extension.requestLLM
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
suspend fun main() {
    simpleOllamaAIExecutor().use { executor ->
        val funcAgent = AIAgent<String, Unit>(
            systemPrompt = "You're a simple chat agent",
            promptExecutor = executor,
            strategy = functionalStrategy {
                var userResponse = it
                while (userResponse != "/bye") {
                    val responses = requestLLM(userResponse)
                    println(responses.content)
                    userResponse = readln()
                }
            },
            llmModel = OllamaModels.Meta.LLAMA_3_2
        )

        println("Simple chat agent started\nUse /bye to quit\nEnter your message:")
        val input = readln()
        funcAgent.run(input)
    }
}
