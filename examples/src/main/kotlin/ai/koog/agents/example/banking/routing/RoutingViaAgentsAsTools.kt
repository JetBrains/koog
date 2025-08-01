package ai.koog.agents.example.banking.routing

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.asTool
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.banking.tools.MoneyTransferTools
import ai.koog.agents.example.banking.tools.TransactionAnalysisTools
import ai.koog.agents.example.banking.tools.bankingAssistantSystemPrompt
import ai.koog.agents.example.banking.tools.transactionAnalysisPrompt
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = ApiKeyService.openAIApiKey // Your OpenAI API key
    val openAIExecutor = simpleOpenAIExecutor(apiKey)

    val transferAgent = AIAgent(
        executor = openAIExecutor,
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = bankingAssistantSystemPrompt,
        temperature = 0.0,
        toolRegistry = ToolRegistry { tools(MoneyTransferTools().asTools()) }
    )

    val analysisAgent = AIAgent(
        executor = openAIExecutor,
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        systemPrompt = bankingAssistantSystemPrompt + transactionAnalysisPrompt,
        temperature = 0.0,
        toolRegistry = ToolRegistry { tools(TransactionAnalysisTools().asTools()) }
    )

    val classifierAgent = AIAgent(
        executor = openAIExecutor,
        llmModel = OpenAIModels.Reasoning.GPT4oMini,
        toolRegistry = ToolRegistry {
            tool(AskUser)
            tool(
                transferAgent.asTool(
                    agentName = "Transfers money and solves all arising problems",
                    agentDescription = "transferMoney",
                    inputDescriptor = ToolParameterDescriptor("request", "Transfer request", ToolParameterType.String)
                )
            )
            tool(
                analysisAgent.asTool(
                    agentName = "analyzeTransactions",
                    agentDescription = "Performs analytics for user transactions",
                    inputDescriptor = ToolParameterDescriptor(
                        "request",
                        "Transaction analytics request",
                        ToolParameterType.String
                    )
                )
            )
        },
        systemPrompt = bankingAssistantSystemPrompt + transactionAnalysisPrompt
    )

    println("Banking Assistant started")
    val message = "Send 25 euros to Daniel for dinner at the restaurant."
    // transfer messages
//        "Send 50 euros to Alice for the concert tickets"
//        "What's my current balance?"
    // analysis messages
//        "How much have I spent on restaurants this month?"
//         "What's my maximum check at a restaurant this month?"
//         "How much did I spend on groceries in the first week of May?"
//         "What's my total spending on entertainment in May?"
    val result = classifierAgent.run(message)
    println(result)
}
