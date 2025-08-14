package ai.koog.agents.example.clients

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.bedrock.BedrockModel
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import kotlinx.coroutines.runBlocking

fun main() {
    val client = simpleBedrockExecutor(
        awsAccessKeyId= System.getenv("AWS_ACCESS_KEY_ID") ?: error("AWS_ACCESS_KEY_ID env is not set"),
        awsSecretAccessKey = System.getenv("AWS_SECRET_KEY") ?: error("AWS_SECRET_ACCESS_KEY env is not set")
    )
    val examplePrompt = prompt("example") {
        system("You are an agent that can answer questions about the universe.")
        user("What is the meaning of life?")
    }
    runBlocking {
        val response = client.execute(
            prompt = examplePrompt,
            model = BedrockModels.AmazonNovaLite,
            tools = emptyList()
        )
        println(response.first().id)
    }
}
