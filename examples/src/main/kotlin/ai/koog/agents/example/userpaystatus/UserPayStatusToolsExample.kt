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
        executor = simpleMistralAIExecutor(ApiKeyService.mistralAIApiKey),
        llmModel = MistralAIModels.MISTRAL_MEDIUM_3_1,
        temperature = 0.0,
        toolRegistry = toolRegistry,
        maxIterations = 200,
    )
    val paymentStatus = paymentsAgent.run("What's the status of my payment? Transaction ID is T1001")

    println("User's payment status: $paymentStatus")
}
