package com.example.spring_ai_java.service.knowledgebase;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.longtermmemory.feature.LongTermMemory;
import ai.koog.agents.longtermmemory.retrieval.SearchStrategy;
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.rag.base.TextDocument;
import ai.koog.rag.base.storage.SearchStorage;
import ai.koog.rag.base.storage.search.SimilaritySearchRequest;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private final PromptExecutor promptExecutor;
    private final SearchStorage<TextDocument, SimilaritySearchRequest> knowledgeBase;

    public KnowledgeBaseService(
        PromptExecutor promptExecutor,
        SearchStorage<TextDocument, SimilaritySearchRequest> knowledgeBase
    ) {
        this.promptExecutor = promptExecutor;
        this.knowledgeBase = knowledgeBase;
    }

    public String createAndRunAgent(String userPrompt) {
        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(knowledgeBase)
            .withSearchStrategy(
                SearchStrategy.builder().similarity().withTopK(4).withSimilarityThreshold(0.70).build()
            )
            .withPromptAugmenter(PromptAugmenter.builder().user().build())
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .systemPrompt("""
                You are an internal knowledge base assistant.
                Answer only from the retrieved knowledge base context.
                If the answer is not supported by the retrieved context, say you do not know.
                """)
            .llmModel(OpenAIModels.Chat.GPT5Nano)
            .toolRegistry(ToolRegistry.builder().build())
            .install(LongTermMemory.Feature, config -> config.retrieval(retrievalSettings))
            .build();

        return (String) agent.run(userPrompt);
    }
}
