package ai.koog.agents.example.userpaystatus

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import ai.koog.prompt.executor.llms.all.simpleMistralAIExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val toolRegistry = ToolRegistry {
        tools(listOf(PaymentStatusTool()))
    }

    val paymentsAgent = AIAgent(
        promptExecutor = simpleMistralAIExecutor(ApiKeyService.mistralAIApiKey),
        llmModel = MistralAIModels.Chat.MistralMedium31,
        temperature = 0.0,
        toolRegistry = toolRegistry,
        maxIterations = 50,
    )
    val paymentStatus = paymentsAgent.run("What's the status of my payment? Transaction ID is T1001")

    println("User's payment status: $paymentStatus")
}
