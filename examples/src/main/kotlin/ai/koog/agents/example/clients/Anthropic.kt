package ai.koog.agents.example.clients

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import kotlinx.coroutines.runBlocking

fun main() {
    val anthropicApiKey = System.getenv("ANTHROPIC_API_KEY") ?: error("ANTHROPIC_API_KEY env is not set")
    val client = simpleAnthropicExecutor(anthropicApiKey)
    val examplePrompt = prompt("example") {
        system("You are an agent that can answer questions about the universe.")
        user("What is the meaning of life?")
    }
    runBlocking {
        val response = client.execute(
            prompt = examplePrompt,
            model = AnthropicModels.Sonnet_3_7,
            tools = emptyList()
        )
        println(response.first().id)
    }


}
