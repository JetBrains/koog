package com.example.spring_ai_java.service.knowledgebase;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * A ToolSet that lets the agent search the knowledge base on demand.
 * Uses Spring AI's VectorStore directly (synchronous Java API)
 * instead of the Koog SearchStorage wrapper.
 */
@LLMDescription("Tools for searching the internal knowledge base.")
public class KnowledgeBaseTools implements ToolSet {

    private final VectorStore vectorStore;

    public KnowledgeBaseTools(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool
    @LLMDescription("Search the knowledge base for documents relevant to a query. Returns the content of the most relevant documents.")
    public String searchKnowledgeBase(
        @LLMDescription("The search query describing what information you need") String query,
        @LLMDescription("Maximum number of documents to return") int count
    ) {
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(count)
                .similarityThreshold(0.5)
                .build()
        );

        if (results.isEmpty()) {
            return "No relevant documents found for: " + query;
        }

        var response = new StringBuilder("Found " + results.size() + " relevant documents:\n\n");
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            response.append("Document ").append(i + 1).append(": ").append(doc.getId()).append("\n");
            response.append("Content: ").append(doc.getText()).append("\n\n");
        }
        return response.toString();
    }
}
