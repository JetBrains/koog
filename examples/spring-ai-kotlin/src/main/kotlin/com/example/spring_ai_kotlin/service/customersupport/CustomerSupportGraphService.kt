package com.example.spring_ai_kotlin.service.customersupport

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.persistence.jdbc.PostgresJdbcPersistenceStorageProvider
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.SimilaritySearchStrategy
import ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Service
import javax.sql.DataSource


@Suppress("unused")
@SerialName("SupportIntent")
@Serializable
enum class SupportIntent {
    ORDER_STATUS,
    CHANGE_ADDRESS,
    REFUND_OR_RETURN,
    OTHER
}

@Serializable
@LLMDescription("Normalized support request extracted from a user message.")
data class SupportRequest(
    @property:LLMDescription("Detected support intent")
    val intent: SupportIntent,

    @property:LLMDescription("Order ID if present, otherwise null")
    val orderId: String? = null,

    @property:LLMDescription("New address if the user wants to change delivery address")
    val newAddress: String? = null,

    @property:LLMDescription("Original user request rewritten clearly for the downstream handler")
    val userRequest: String
)

data class ContextCheckResult(
    val request: SupportRequest,
    val needsMoreInfo: Boolean,
    val clarificationQuestion: String? = null
)

@LLMDescription("Tools for order lookup, delivery changes, and refund policy checks.")
class EcommerceSupportTools : ToolSet {

    @Tool
    @LLMDescription("Get the current status of an order by order ID.")
    fun getOrderStatus(
        @LLMDescription("Customer order ID") orderId: String
    ): String {
        return """{"orderId":"$orderId","status":"In transit","eta":"Tomorrow"}"""
    }

    @Tool
    @LLMDescription("Change the delivery address of an order if it is still eligible.")
    fun changeDeliveryAddress(
        @LLMDescription("Customer order ID") orderId: String,
        @LLMDescription("New delivery address") newAddress: String
    ): String {
        return """{"orderId":"$orderId","updated":true,"newAddress":"$newAddress"}"""
    }

    @Tool
    @LLMDescription("Check whether an order is eligible for refund or return.")
    fun checkRefundEligibility(
        @LLMDescription("Customer order ID") orderId: String
    ): String {
        return """{"orderId":"$orderId","eligible":true,"window":"30 days"}"""
    }

    @Tool
    @LLMDescription("Answer a general store policy question.")
    fun getPolicy(
        @LLMDescription("Policy topic, for example returns, refunds, shipping")
        topic: String
    ): String {
        return when (topic.lowercase()) {
            "returns", "refunds" -> "Returns are accepted within 30 days for eligible items."
            "shipping" -> "Standard shipping takes 3 to 5 business days."
            else -> "No matching policy found."
        }
    }
}

class EcommerceSupportRollbackTools : ToolSet {
    @Tool
    @LLMDescription("Change the delivery address for an order if the order is still eligible.")
    fun changeDeliveryAddressToHome(
        @LLMDescription("The customer order ID") orderId: String,
        @LLMDescription("The new delivery address") newAddress: String
    ): String {
        // Replace with a real API call
        return """{"orderId":"$orderId","updated":true,"newAddress":"$newAddress"}"""
    }
}

@Service
class CustomerSupportGraphService(
    private val promptExecutor: PromptExecutor,
    private val chatStorage: ChatHistoryProvider,
    private val dataSource: DataSource,
    private val knowledgeBase: SearchStorage<TextDocument, SimilaritySearchRequest>
) {

    @OptIn(ExperimentalAgentsApi::class)
    suspend fun createAndRunAgent(userPrompt: String, sessionId: String): String {
        val systemPrompt = """
            You are an e-commerce support assistant.
            Be concise and policy-aware.
            Never invent order data.
            If order context is missing for an order-specific request, ask for it.
        """.trimIndent()

        val agentConfig = AIAgentConfig(
            prompt = prompt("ecommerce-support-level1") {
                system(systemPrompt)
            },
            model = OpenAIModels.Chat.GPT5Nano,
            maxAgentIterations = 20
        )

        val supportTools = EcommerceSupportTools()
        val rollbackTools = EcommerceSupportRollbackTools()

        val agent = AIAgent<String, String>(
            promptExecutor = promptExecutor,
            strategy = createControllableWorkflow(systemPrompt), // See IntentProcessingFlowchart.mermaid
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tools(supportTools)
            }
        ) {
            // Let the agent remember previous conversations:
            install(ChatMemory) {
                chatHistoryProvider = chatStorage
                windowSize(20)
            }

            // Augment user requests with external knowledge from our vector database:
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

            // Make agent fault-tolerant using Koog's persistence.
            // The agent will recover from the exact graph node where it crashed:
            install(Persistence) {
                // Configure where to store the checkpoints:
                storage = PostgresJdbcPersistenceStorageProvider(dataSource)

                enableAutomaticPersistence = true

                // Configure how to roll back side effects produced by specific tools:
                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(
                        toolFunction = supportTools::changeDeliveryAddress,
                        rollbackToolFunction = rollbackTools::changeDeliveryAddressToHome
                    )
                }
            }
        }

        return agent.run(
            "My order 84721 hasn't arrived yet. Where is it?"
        )
    }

    // See IntentProcessingFlowchart.mermaid
    private fun createControllableWorkflow(systemPrompt: String): AIAgentGraphStrategy<String, String> =
        strategy<String, String>("ecommerce_support_level1") {

            // 1) Detect Intent
            val classifyRequest by nodeLLMRequestStructured<SupportRequest>(
                examples = listOf(
                    SupportRequest(
                        intent = SupportIntent.ORDER_STATUS,
                        orderId = "84721",
                        userRequest = "Check the status of order 84721"
                    ),
                    SupportRequest(
                        intent = SupportIntent.CHANGE_ADDRESS,
                        orderId = "84721",
                        newAddress = "12 King Street",
                        userRequest = "Change the delivery address for order 84721 to 12 King Street"
                    ),
                    SupportRequest(
                        intent = SupportIntent.REFUND_OR_RETURN,
                        orderId = "84721",
                        userRequest = "I want to return order 84721"
                    ),
                    SupportRequest(
                        intent = SupportIntent.OTHER,
                        userRequest = "What is your refund policy?"
                    )
                ),
                fixingParser = StructureFixingParser(
                    model = OpenAIModels.Chat.GPT5Nano,
                    retries = 2
                )
            )

            // 2) Check Context
            val checkContext by node<SupportRequest, ContextCheckResult> { req ->
                val orderRequired = req.intent in setOf(
                    SupportIntent.ORDER_STATUS,
                    SupportIntent.CHANGE_ADDRESS,
                    SupportIntent.REFUND_OR_RETURN
                )

                when {
                    orderRequired && req.orderId.isNullOrBlank() ->
                        ContextCheckResult(
                            request = req,
                            needsMoreInfo = true,
                            clarificationQuestion = "Please provide your order number so I can help with that request."
                        )

                    req.intent == SupportIntent.CHANGE_ADDRESS && req.newAddress.isNullOrBlank() ->
                        ContextCheckResult(
                            request = req,
                            needsMoreInfo = true,
                            clarificationQuestion = "What new delivery address would you like to use?"
                        )

                    else ->
                        ContextCheckResult(
                            request = req,
                            needsMoreInfo = false
                        )
                }
            }

            // 3a) Ask for missing info
            val askForMoreInfo by subgraphWithTask<ContextCheckResult, String> { ctx ->
                ctx.clarificationQuestion ?: "Please provide the missing information."
            }

            data class AccountIssueSolution(val actionsTaken: List<String>)
            data class UserResponse(val confirmed: Boolean, val feedback: String)

            val askUserConfirmation by node<AccountIssueSolution, UserResponse> { input ->
                println("WDYT about solution: ${input.actionsTaken} ?")
                UserResponse(confirmed = true, feedback = "I think this solution is good")
            }

            // 3b) Order status handler
            val orderStatusFlow by subgraphWithTask<SupportRequest, String>(
                tools = EcommerceSupportTools().asTools()
            ) { req ->
                """
                        $systemPrompt
            
                        Handle this request as an ORDER STATUS case.
                        Use the order status tool and then answer the user clearly.
            
                        Request: ${req.userRequest}
                        Order ID: ${req.orderId}
                    """.trimIndent()
            }

            // 3c) Change address handler
            val changeAddressFlow by subgraphWithTask<SupportRequest, String>(
                tools = EcommerceSupportTools().asTools()
            ) { req ->
                """
                        $systemPrompt
            
                        Handle this request as a CHANGE ADDRESS case.
                        Use the address-change tool if possible and explain the result.
            
                        Request: ${req.userRequest}
                        Order ID: ${req.orderId}
                        New address: ${req.newAddress}
                    """.trimIndent()
            }

            // 3d) Refund / return handler
            val refundFlow by subgraphWithTask<SupportRequest, String>(
                tools = EcommerceSupportTools().asTools()
            ) { req ->
                """
                        $systemPrompt
            
                        Handle this request as a REFUND OR RETURN case.
                        Check eligibility first, then explain next steps.
            
                        Request: ${req.userRequest}
                        Order ID: ${req.orderId}
                    """.trimIndent()
            }

            // 3e) Fallback FAQ / policy handler
            val faqFlow by subgraphWithTask<SupportRequest, String>(
                tools = EcommerceSupportTools().asTools()
            ) { req ->
                """
                        $systemPrompt
            
                        Handle this request as a GENERAL FAQ / POLICY case.
                        Prefer using the policy tool when relevant.
            
                        Request: ${req.userRequest}
                    """.trimIndent()
            }

            val compressLLMHistory by nodeLLMCompressHistory<String>(
                strategy = HistoryCompressionStrategy.Chunked(20)
            )

            val maybeCompressHistory by nodeDoNothing<String>()

            // Graph edges
            edge(nodeStart forwardTo classifyRequest)

            edge(
                classifyRequest forwardTo checkContext
                    onCondition { it.isSuccess }
                    transformed { it.getOrThrow().data }
            )

            edge(
                classifyRequest forwardTo maybeCompressHistory
                    onCondition { it.isFailure }
                    transformed { "Sorry, I couldn't understand the request well enough. Please rephrase it." }
            )

            edge(
                checkContext forwardTo askForMoreInfo
                    onCondition { it.needsMoreInfo }
            )

            edge(
                checkContext forwardTo orderStatusFlow
                    onCondition { !it.needsMoreInfo && it.request.intent == SupportIntent.ORDER_STATUS }
                    transformed { it.request }
            )

            edge(
                checkContext forwardTo changeAddressFlow
                    onCondition { !it.needsMoreInfo && it.request.intent == SupportIntent.CHANGE_ADDRESS }
                    transformed { it.request }
            )

            edge(
                checkContext forwardTo refundFlow
                    onCondition { !it.needsMoreInfo && it.request.intent == SupportIntent.REFUND_OR_RETURN }
                    transformed { it.request }
            )

            edge(
                checkContext forwardTo faqFlow
                    onCondition { !it.needsMoreInfo && it.request.intent == SupportIntent.OTHER }
                    transformed { it.request }
            )

            edge(askForMoreInfo forwardTo maybeCompressHistory)
            edge(orderStatusFlow forwardTo maybeCompressHistory)
            edge(changeAddressFlow forwardTo maybeCompressHistory)
            edge(refundFlow forwardTo maybeCompressHistory)
            edge(faqFlow forwardTo maybeCompressHistory)

            edge(maybeCompressHistory forwardTo compressLLMHistory onCondition { tooManyTokensSpent() })
            edge(maybeCompressHistory forwardTo nodeFinish onCondition { !tooManyTokensSpent() })
            edge(compressLLMHistory forwardTo nodeFinish)
        }

    private fun AIAgentGraphContextBase.tooManyTokensSpent(): Boolean = llm.prompt.latestTokenUsage > 100500

}
