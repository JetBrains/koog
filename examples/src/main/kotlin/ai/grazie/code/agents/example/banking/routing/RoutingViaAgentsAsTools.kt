package ai.grazie.code.agents.example.banking.routing

import ai.grazie.code.agents.core.agent.asTool
import ai.grazie.code.agents.core.api.simpleChatAgent
import ai.grazie.code.agents.core.api.simpleSingleRunAgent
import ai.grazie.code.agents.core.tools.SimpleToolRegistry
import ai.grazie.code.agents.core.tools.reflect.toolsFrom
import ai.grazie.code.agents.core.tools.tools.AskUser
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.example.banking.tools.MoneyTransferTools
import ai.grazie.code.agents.example.banking.tools.TransactionAnalysisTools
import ai.grazie.code.agents.example.banking.tools.bankingAssistantSystemPrompt
import ai.grazie.code.agents.example.banking.tools.transactionAnalysisPrompt
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = TokenService.openAIToken // Your OpenAI API key
    val openAIExecutor = simpleOpenAIExecutor(apiKey)

    val transferAgent = simpleChatAgent(
        executor = openAIExecutor,
        systemPrompt = bankingAssistantSystemPrompt,
        cs = this,
        temperature = 0.0,
        toolRegistry = SimpleToolRegistry { toolsFrom(MoneyTransferTools()) }
    )

    val analysisAgent = simpleChatAgent(
        executor = openAIExecutor,
        systemPrompt = bankingAssistantSystemPrompt + transactionAnalysisPrompt,
        cs = this,
        temperature = 0.0,
        toolRegistry = SimpleToolRegistry { toolsFrom(TransactionAnalysisTools()) }
    )

    val classifierAgent = simpleSingleRunAgent(
        executor = openAIExecutor,
        toolRegistry = SimpleToolRegistry {
            tool(AskUser)
            tool(
                transferAgent.asTool(
                    "Transfers money and solves all arising problems",
                    name = "transferMoney"
                )
            )
            tool(
                analysisAgent.asTool(
                    "Performs analytics for user transactions",
                    name = "analyzeTransactions"
                )
            )
        },
        systemPrompt = bankingAssistantSystemPrompt + transactionAnalysisPrompt,
        cs = this
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
    val result = classifierAgent.runAndGetResult(message)
    println(result)
}
