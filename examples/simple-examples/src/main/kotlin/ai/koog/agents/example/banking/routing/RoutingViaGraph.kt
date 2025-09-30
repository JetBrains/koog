package ai.koog.agents.example.banking.routing

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.banking.tools.MoneyTransferTools
import ai.koog.agents.example.banking.tools.TransactionAnalysisTools
import ai.koog.agents.example.banking.tools.bankingAssistantSystemPrompt
import ai.koog.agents.example.banking.tools.transactionAnalysisPrompt
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.structure.StructureFixingParser
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val apiKey = ApiKeyService.openAIApiKey // Your OpenAI API key

    val toolRegistry = ToolRegistry {
        tool(AskUser)
        tools(MoneyTransferTools().asTools())
        tools(TransactionAnalysisTools().asTools())
    }

    val strategy = strategy<String, String>("banking assistant") {
        val classifyRequest by subgraph<String, ClassifiedBankRequest>(
            tools = listOf(AskUser)
        ) {
            val requestClassification by nodeLLMRequestStructured<ClassifiedBankRequest>(
                examples = listOf(
                    ClassifiedBankRequest(
                        requestType = RequestType.Transfer,
                        userRequest = "Send 25 euros to Daniel for dinner at the restaurant."
                    ),
                    ClassifiedBankRequest(
                        requestType = RequestType.Analytics,
                        userRequest = "Provide transaction overview for the last month"
                    )
                ),
                fixingParser = StructureFixingParser(
                    fixingModel = OpenAIModels.CostOptimized.GPT4oMini,
                    retries = 2,
                )
            )

            val callLLM by nodeLLMRequest()
            val callAskUserTool by nodeExecuteTool()

            edge(nodeStart forwardTo requestClassification)
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
            edge(
                callLLM forwardTo callLLM onAssistantMessage { true }
                    transformed { "Please call `${AskUser.name}` tool instead of chatting" }
            )
            edge(callAskUserTool forwardTo requestClassification transformed { it.result.toString() })
        }

        val transferMoney by subgraphWithTask<ClassifiedBankRequest, String>(
            tools = MoneyTransferTools().asTools() + AskUser,
            llmModel = OpenAIModels.Chat.GPT4o
        ) { request ->
            """
                $bankingAssistantSystemPrompt
                Specifically, you need to help with the following request:
                ${request.userRequest}
            """.trimIndent()
        }

        val transactionAnalysis by subgraphWithTask<ClassifiedBankRequest, String>(
            tools = TransactionAnalysisTools().asTools() + AskUser,
        ) { request ->
            """
                $bankingAssistantSystemPrompt
                $transactionAnalysisPrompt
                Specifically, you need to help with the following request:
                ${request.userRequest}
            """.trimIndent()
        }
        edge(nodeStart forwardTo classifyRequest)

        edge(classifyRequest forwardTo transferMoney onCondition { it.requestType == RequestType.Transfer })
        edge(classifyRequest forwardTo transactionAnalysis onCondition { it.requestType == RequestType.Analytics })

        // assume that subgraph always returns successfull results
        edge(transferMoney forwardTo nodeFinish)
        edge(transactionAnalysis forwardTo nodeFinish)
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

    val agent = AIAgent<String, String>(
        promptExecutor = simpleOpenAIExecutor(apiKey),
        strategy = strategy,
        agentConfig = agentConfig,
        toolRegistry = toolRegistry,
    )

    println("Banking Assistant started")
    val message = "Send 25 euros to Daniel for dinner at the restaurant."
//     transfer messages
//        "Send 50 euros to Alice for the concert tickets"
//        "What's my current balance?"
//     analysis messages
//        "How much have I spent on restaurants this month?"
//         "What's my maximum check at a restaurant this month?"
//         "How much did I spend on groceries in the first week of May?"
//         "What's my total spending on entertainment in May?"
    val result = agent.run(message)
    println(result)
}
