package com.example.spring_ai_kotlin.service.customersupport

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.agents.features.persistence.jdbc.PostgresJdbcPersistenceStorageProvider
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class CustomerSupportPersistentService(
    private val promptExecutor: PromptExecutor,
    private val chatStorage: ChatHistoryProvider,
    private val dataSource: DataSource
) {

    @Tool
    @LLMDescription("Get the current status of an order by order ID.")
    fun getOrderStatus(
        @LLMDescription("The customer order ID") orderId: String
    ): String {
        // Replace with a real API call
        return """{"orderId":"$orderId","status":"Shipped","canChangeAddress":true}"""
    }

    @Tool
    @LLMDescription("Change the delivery address for an order if the order is still eligible.")
    fun changeDeliveryAddress(
        @LLMDescription("The customer order ID") orderId: String,
        @LLMDescription("The new delivery address") newAddress: String
    ): String {
        // Replace with a real API call
        return """{"orderId":"$orderId","updated":true,"newAddress":"$newAddress"}"""
    }

    @Tool
    @LLMDescription("Look up a support policy, such as refund policy or address-change policy.")
    fun getPolicy(
        @LLMDescription("Policy name, for example refund, returns, address-change")
        policyName: String
    ): String {
        return when (policyName.lowercase()) {
            "address-change" ->
                "Address changes are allowed until the parcel is handed to the carrier."

            "refund" ->
                "Refunds are allowed within 30 days for eligible items."

            else ->
                "No matching policy found."
        }
    }

    // for rollback
    @Tool
    @LLMDescription("Change the delivery address for an order if the order is still eligible.")
    fun changeDeliveryAddressToHome(
        @LLMDescription("The customer order ID") orderId: String,
        @LLMDescription("The new delivery address") newAddress: String
    ): String {
        // Replace with a real API call
        return """{"orderId":"$orderId","updated":true,"newAddress":"$newAddress"}"""
    }

    suspend fun createAndRunAgent(userPrompt: String, sessionId: String): String {
        val toolRegistry = ToolRegistry {
            tool(::getOrderStatus.asTool())
            tool(::changeDeliveryAddress.asTool())
            tool(::getPolicy.asTool())
        }

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT5Nano,
            systemPrompt = """
                You are an e-commerce support agent.
                Use tools to verify order state and policies before answering.
                Do not guess order status or policy details.
            """.trimIndent(),
            strategy = reActStrategy(),
            toolRegistry = toolRegistry
        ) {
            install(ChatMemory) {
                chatHistoryProvider = chatStorage
                windowSize(20)
            }
            install(Persistence) {
                storage = PostgresJdbcPersistenceStorageProvider(dataSource)
                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(toolFunction = ::changeDeliveryAddress, rollbackToolFunction = ::changeDeliveryAddressToHome)
                }
            }
        }

        return agent.run(
            userPrompt, // "My order 84721 is on the way. Can you change the delivery address to 12 King Street?"
            sessionId // "customer-123"
        )
    }
}