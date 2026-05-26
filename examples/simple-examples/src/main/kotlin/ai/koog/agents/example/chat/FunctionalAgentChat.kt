package ai.koog.agents.example.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.MessagePart
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
                    val response = requestLLM(userResponse)
                    println(response.parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text })
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
