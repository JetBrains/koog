package com.example.spring_ai_kotlin.service.customersupport

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.stereotype.Service

@Service
class AiAgentService(
    chatClientBuilder: ChatClient.Builder,
    vectorStore: VectorStore,
    chatMemory: ChatMemory,
) {

    // Build a fully configured ChatClient once at construction time
    private val chatClient: ChatClient = chatClientBuilder
        .defaultSystem("""
            You are an e-commerce support assistant.
            Be concise and policy-aware.
            Never invent order data — always use the provided tools.
            If order context is missing for an order-specific request, ask the user for the order ID.
            Use retrieved policy documents to answer refund/return/shipping questions accurately.
        """.trimIndent())
        .defaultAdvisors(
            // Vector store RAG advisor – enriches every prompt with relevant docs
            QuestionAnswerAdvisor.builder(vectorStore).searchRequest(SearchRequest.builder().build()).build(),
            // Sliding-window chat memory advisor – keeps last N turns per session
            MessageChatMemoryAdvisor.builder(chatMemory).build()
        )
        .build()

    suspend fun createAndRunAgent(userPrompt: String, sessionId: String): String? =
        chatClient.prompt()
            .user(userPrompt)
            .tools()
            .advisors { a -> a.param(ChatMemory.CONVERSATION_ID, sessionId) }  // scope memory to session
            .call()
            .content()
}
