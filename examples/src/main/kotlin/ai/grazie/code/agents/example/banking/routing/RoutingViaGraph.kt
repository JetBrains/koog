package ai.grazie.code.agents.example.banking.routing

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.tools.SimpleToolRegistry
import ai.grazie.code.agents.core.tools.reflect.asTools
import ai.grazie.code.agents.core.tools.reflect.toolsFrom
import ai.grazie.code.agents.core.tools.tools.AskUser
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.example.banking.tools.MoneyTransferTools
import ai.grazie.code.agents.example.banking.tools.TransactionAnalysisTools
import ai.grazie.code.agents.example.banking.tools.bankingAssistantSystemPrompt
import ai.grazie.code.agents.example.banking.tools.transactionAnalysisPrompt
import ai.grazie.code.prompt.structure.json.JsonSchemaGenerator
import ai.grazie.code.prompt.structure.json.JsonStructuredData
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = TokenService.openAIToken // Your OpenAI API key

    val toolRegistry = SimpleToolRegistry {
        tool(AskUser)
        toolsFrom(MoneyTransferTools())
        toolsFrom(TransactionAnalysisTools())
    }

    val strategy = strategy("banking assistant") {
        stage {
            val classifyRequest by subgraph<String, ClassifiedBankRequest>(
                tools = listOf(AskUser)
            ) {
                val requestClassification by nodeLLMRequestStructured(
                    structure = JsonStructuredData.createJsonStructure<ClassifiedBankRequest>(
                        schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
                        examples = listOf(
                            ClassifiedBankRequest(
                                requestType = RequestType.Transfer,
                                userRequest = "Send 25 euros to Daniel for dinner at the restaurant."
                            ),
                            ClassifiedBankRequest(
                                requestType = RequestType.Analytics,
                                userRequest = "Provide transaction overview for the last month"
                            )
                        )
                    ),
                    retries = 2,
                    fixingModel = OpenAIModels.CostOptimized.GPT4oMini
                )

                val callLLM by nodeLLMRequest()
                val callAskUserTool by nodeExecuteTool()

                edge(nodeStart forwardTo requestClassification transformed { stageInput })
                edge(
                    requestClassification forwardTo nodeFinish
                            onCondition { it.isSuccess }
                            transformed { it.getOrThrow().structure }
                )
                edge(
                    requestClassification forwardTo callLLM
                            onCondition { it.isFailure }
                            transformed { "Failed to understand the user's intent" }
                )
                edge(callLLM forwardTo callAskUserTool onToolCall { true })
                edge(callLLM forwardTo callLLM onAssistantMessage { true }
                        transformed { "Please call `${AskUser.name}` tool instead of chatting" })
                edge(callAskUserTool forwardTo requestClassification transformed { it.result.toString() })
            }


            val transferMoney by subgraphWithTask<ClassifiedBankRequest>(
                tools = MoneyTransferTools().asTools() + AskUser,
                shouldTLDRHistory = true,
                model = OpenAIModels.Chat.GPT4o
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

    val agentConfig = AIAgentConfig(
        prompt = prompt(
            id = "banking assistant"
        ) {
            system(bankingAssistantSystemPrompt + transactionAnalysisPrompt)
        },
        model = OpenAIModels.Chat.GPT4o,
        maxAgentIterations = 50
    )

    val agent = AIAgent(
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