package com.example.spring_ai_java.service.customersupport;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Alternative customer support implementation using Spring AI's ChatClient directly,
 * without a Koog agent graph. Demonstrates how to layer RAG and chat memory via
 * Spring AI advisors.
 */
@Service
public class SpringAICustomerSupportAgent {

    private final ChatClient chatClient;

    public SpringAICustomerSupportAgent(
        ChatClient.Builder chatClientBuilder,
        VectorStore vectorStore,
        ChatMemory chatMemory
    ) {
        this.chatClient = chatClientBuilder
            .defaultSystem("""
                You are an e-commerce support assistant.
                Be concise and policy-aware.
                Never invent order data — always use the provided tools.
                If order context is missing for an order-specific request, ask the user for the order ID.
                Use retrieved policy documents to answer refund/return/shipping questions accurately.
                """)
            .defaultAdvisors(
                // Vector store RAG advisor – enriches every prompt with relevant docs
                QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder().build())
                    .build(),
                // Sliding-window chat memory advisor – keeps last N turns per session
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
            .build();
    }

    public String createAndRunAgent(String userPrompt, String sessionId) {
        return chatClient.prompt()
            .user(userPrompt)
            .tools()
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
            .call()
            .content();
    }
}
