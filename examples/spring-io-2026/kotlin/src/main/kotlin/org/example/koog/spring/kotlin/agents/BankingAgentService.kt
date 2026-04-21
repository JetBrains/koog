package org.example.koog.spring.kotlin.agents.customersupport

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.agents.features.persistence.jdbc.PostgresJdbcPersistenceStorageProvider
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.SimilaritySearchStrategy
import ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import org.example.koog.spring.kotlin.structs.AccountIssueSolution
import org.example.koog.spring.kotlin.structs.AccountIssueSummary
import org.example.koog.spring.kotlin.structs.UserResponse
import org.example.koog.spring.kotlin.tools.AccountReadUtils
import org.example.koog.spring.kotlin.tools.AccountWriteUtils
import org.example.koog.spring.kotlin.tools.CommunicationUtils
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.sql.DataSource

@Service
class BankingAgentService(
    private val promptExecutor: PromptExecutor,
    private val chatStorage: ChatHistoryProvider,
    private val dataSource: DataSource,
    private val knowledgeBase: SearchStorage<TextDocument, SimilaritySearchRequest>
) {

    suspend fun launchSupportAgent(userSessionId: String, question: String): String {
        val agent: AIAgent<String, AccountIssueSolution> = createAgent(userSessionId)

        return agent.run(question, userSessionId).actionsTaken
    }


    // sessionId -> set of agentIds (Boolean.TRUE to emulate Kotlin's Unit placeholder)
    private val agentByUser: ConcurrentMap<String, AIAgent<String, AccountIssueSolution>> =
        ConcurrentHashMap<String, AIAgent<String, AccountIssueSolution>>()

    @OptIn(ExperimentalAgentsApi::class)
    private fun createAgent(userId: String): AIAgent<String, AccountIssueSolution> {
        val existingAgent = agentByUser[userId]
        if (existingAgent != null) {
            return existingAgent
        }

        val communicationTools = CommunicationUtils()
        val databaseReadTools = AccountReadUtils(userId)
        val databaseWriteTools = AccountWriteUtils(userId)

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            toolRegistry = ToolRegistry {
                tools(communicationTools)
                tools(databaseReadTools)
                tools(databaseWriteTools)
            },
            agentConfig = AIAgentConfig(
                // Initial prompt to start with:
                prompt = prompt("id") {
                    system("You are a banking assistant responsible for helping users with their orders")
//                    assistant("aaa")
//                    user("")
//                    tool {
//                        call("id", "f", "x=1")
//                        result("id", "f", "100")
//                    }
                },
                model = OpenAIModels.Chat.GPT5_2,
                maxAgentIterations = 200
            ),
            strategy = createStrategy(communicationTools, databaseReadTools, databaseWriteTools)
        ) {
            install(ChatMemory) {
                chatHistoryProvider = chatStorage
                windowSize(50)
            }
            install(Persistence) {
                storage = PostgresJdbcPersistenceStorageProvider(dataSource)
                enableAutomaticPersistence = true
            }

            install(LongTermMemory) {
                retrieval {
                    storage = knowledgeBase
                    searchStrategy = SimilaritySearchStrategy(
                        topK = 4,
                        similarityThreshold = 0.70
                    )
                    promptAugmenter = UserPromptAugmenter()
                }
            }
        }

        agentByUser[userId] = agent

        return agent
    }

    private fun createStrategy(
        communicationTools: ToolSet,
        databaseReadTools: ToolSet,
        databaseWriteTools: ToolSet
    ): AIAgentGraphStrategy<String, AccountIssueSolution> =
        strategy<String, AccountIssueSolution>("custom-banking-strategy") {
            val identifyProblem by subgraphWithTask<String, AccountIssueSummary>(
                tools = communicationTools.asTools() + databaseReadTools.asTools(),
                llmModel = OpenAIModels.Chat.GPT5_2,
            ) { input -> "Identify the problem with the user and formulate a problem description:\n$input" }

            val fixProblem by subgraphWithTask<AccountIssueSummary, AccountIssueSolution>(
                tools = databaseReadTools.asTools() + databaseWriteTools.asTools(),
                llmModel = AnthropicModels.Sonnet_4
            ) { description -> "Now solve the user's problem: \n$description" }

            val verifySolution by subgraphWithVerification<AccountIssueSolution>(
                tools = communicationTools.asTools() + databaseReadTools.asTools(),
                llmModel = OpenAIModels.Chat.O3,
            ) { solution -> "Now verify that the problem is actually solved:\n$solution" }

            // TODO: Unused example node -- feel free to put anywhere in the strategy
            val askUserConfirmation by node<AccountIssueSolution, UserResponse> { solution ->
                // TODO: send via HTTTP and present solution in UI
                println("What do you think about this solution: ${solution.actionsTaken}")
                UserResponse(true, "I think this solution is good")
            }

            val adjustSolution by subgraphWithTask<String, AccountIssueSolution>(
                tools = communicationTools.asTools() + databaseReadTools.asTools(),
                llmModel = AnthropicModels.Sonnet_4_6
            ) { feedback -> "Fix the solution based on the provided feedback:\n$feedback" }

            val compressHistory by nodeLLMCompressHistory<AccountIssueSolution>(
                strategy = RetrieveFactsFromHistory(
                    Concept(
                        keyword = "important-steps",
                        description = "What steps were important to fix the problem",
                        factType = FactType.MULTIPLE
                    ),
                    Concept(
                        keyword = "suspicious-operation",
                        description = "What banking operations were suspicious",
                        factType = FactType.MULTIPLE
                    )
                )
            )

            edge(nodeStart forwardTo identifyProblem)
            edge(identifyProblem forwardTo fixProblem)
            edge(fixProblem forwardTo verifySolution)
            edge(verifySolution forwardTo nodeFinish onCondition { it.successful } transformed { it.input })
            edge(verifySolution forwardTo adjustSolution onCondition { !it.successful } transformed { it.feedback })
            edge(adjustSolution forwardTo compressHistory onCondition { historyIsTooLong() })
            edge(adjustSolution forwardTo verifySolution onCondition { !historyIsTooLong() })
            edge(compressHistory forwardTo verifySolution)

        }

    private suspend fun AIAgentGraphContextBase.historyIsTooLong(): Boolean = llm.readSession {
        prompt.latestTokenUsage > 100500
    }
}
