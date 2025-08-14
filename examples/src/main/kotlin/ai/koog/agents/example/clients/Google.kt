package ai.koog.agents.example.clients

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import kotlinx.coroutines.runBlocking

fun main() {
    val googleApiKey = System.getenv("GOOGLE_API_KEY") ?: error("GOOGLE_API_KEY env is not set")
    val client = simpleGoogleAIExecutor(googleApiKey)
    val examplePrompt = prompt("example") {
        system("You are an agent that can answer questions about the universe.")
        user("What is the meaning of life?")
    }
    runBlocking {
        val response = client.execute(
            prompt = examplePrompt,
            model = GoogleModels.Gemini1_5Pro,
            tools = emptyList()
        )
        println(response.first().id)
    }
}
