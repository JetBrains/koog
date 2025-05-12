package ai.grazie.code.agents.example.banking.routing

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.subgraphWithTask
import ai.grazie.code.agents.core.tools.SimpleToolRegistry
import ai.grazie.code.agents.core.tools.reflect.asTools
import ai.grazie.code.agents.core.tools.reflect.toolsFrom
import ai.grazie.code.agents.core.tools.tools.AskUser
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.example.banking.tools.MoneyTransferTools
import ai.grazie.code.agents.example.banking.tools.TransactionAnalysisTools
import ai.grazie.code.agents.example.banking.tools.bankingAssistantSystemPrompt
import ai.grazie.code.agents.example.banking.tools.transactionAnalysisPrompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking


fun main() = runBlocking {
    val apiKey =
//        System.getenv("ANTHROPIC_API_KEY") //?:
        System.getenv("OPENAI_API_KEY") ?:
        TokenService.openAIToken // Your OpenAI API key

    val toolRegistry = SimpleToolRegistry {
        tool(AskUser)
        toolsFrom(MoneyTransferTools())
        toolsFrom(TransactionAnalysisTools())
    }

    val strategy = strategy("banking assistant") {
        stage {
            val classifyRequest by subgraphWithTask<ClassifiedBankRequest, String>(
                tools = listOf(AskUser),
                shouldTLDRHistory = false,
                finishTool = ProvideClassifiedRequest,
            ) { input ->
                """
                    Classify the user's request and provide a classification result.
                    If you are not sure, please ask the user.
                    
                    If the user request relates to money transfers, classify it as Transfer. 
                    If the user request relates to transaction analytics, classify it as Analytics.
                    
                    When you are done, call the finish tool named "provide_classified_request"
                     in order to provide your classification result.
                """.trimIndent()
            }
            val transferMoney by subgraphWithTask<ClassifiedBankRequest>(
                tools = MoneyTransferTools().asTools() + AskUser,
                shouldTLDRHistory = false
            ) { request ->
                """
                    ${bankingAssistantSystemPrompt}
                    Specifically, you need to help with the following request:
                    ${request.userRequest}
                """.trimIndent()
            }

            val transactionAnalysis by subgraphWithTask<ClassifiedBankRequest>(
                tools = TransactionAnalysisTools().asTools() + AskUser,
                shouldTLDRHistory = false
            ) { request ->
                """
                    ${bankingAssistantSystemPrompt}
                    ${transactionAnalysisPrompt}
                    Specifically, you need to help with the following request:
                    ${request.userRequest}
                """.trimIndent()
            }
            edge(nodeStart forwardTo classifyRequest transformed { stageInput })
            edge(classifyRequest forwardTo transferMoney onCondition { it.requestType == RequestType.Transfer })
            edge(classifyRequest forwardTo transactionAnalysis onCondition { it.requestType == RequestType.Analytics })
            edge(transferMoney forwardTo nodeFinish transformed { it.result })
            edge(transactionAnalysis forwardTo nodeFinish transformed { it.result })
        }
    }

    val agentConfig = LocalAgentConfig(
        prompt = prompt(
            llm = OpenAIModels.GPT4o,
//            llm = AnthropicModels.Sonnet_3_5,
            id = "banking assistant") {
            system(bankingAssistantSystemPrompt + transactionAnalysisPrompt)
        },
        maxAgentIterations = 50
    )

    val agent = AIAgentBase(
        cs = this,
        toolRegistry = toolRegistry,
        strategy = strategy,
        agentConfig = agentConfig,
        promptExecutor = simpleOpenAIExecutor(apiKey),
//        promptExecutor = simpleAnthropicExecutor(apiKey),
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
    val result = agent.runAndGetResult(message)
    println(result)
}