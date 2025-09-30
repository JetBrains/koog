package ai.koog.agents.example.client

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.simpleapi.Switch
import ai.koog.agents.example.simpleapi.SwitchTools
import ai.koog.prompt.executor.clients.bedrock.BedrockClientSettings
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.BedrockRegions
import ai.koog.prompt.executor.llms.all.simpleBedrockExecutor
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val switch = Switch()

    val toolRegistry = ToolRegistry {
        tools(SwitchTools(switch).asTools())
    }

    // Create Bedrock client settings
    val bedrockSettings = BedrockClientSettings(
        region = BedrockRegions.US_WEST_2.regionCode, // Change this to your preferred region
        maxRetries = 3
    )

    // Create Bedrock LLM client
    val executor = simpleBedrockExecutor(
        awsAccessKeyId = ApiKeyService.awsAccessKey,
        awsSecretAccessKey = ApiKeyService.awsSecretAccessKey,
        settings = bedrockSettings
    )

    val agent = AIAgent(
        promptExecutor = executor,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        llmModel = BedrockModels.AnthropicClaude4Sonnet, // Use Claude 3.5 Sonnet
        systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
        temperature = 0.0,
        toolRegistry = toolRegistry
    )

    println("Bedrock Agent with Switch Tools - Chat started")
    println("You can ask me to turn the switch on/off or check its current state.")
    println("Type your request:")

    val input = readln()
    println(agent.run(input))
}
