package com.example.spring_ai_kotlin.service.customersupport

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.ext.agent.reActStrategy
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import org.springframework.stereotype.Service

@Service
class CustomerSupportSimpleService(
    private val promptExecutor: PromptExecutor,
    private val chatStorage: ChatHistoryProvider
) {

    @LLMDescription("Tools for order lookup, delivery changes, and refund policy checks.")
    class EcommerceSupportTools : ToolSet {

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
    }

    suspend fun createAndRunAgent(userPrompt: String, sessionId: String): String {
        val toolset = EcommerceSupportTools()
        val toolRegistry = ToolRegistry {
            tools(toolset)
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
        }

        return agent.run(
            userPrompt, // "My order 84721 is on the way. Change the delivery address to 12 King Street and reply with the word DONE. If not possible then reply with the word IMPOSSIBLE. Do not ask me any questions. "
            sessionId // "customer-123"
        )
    }
}