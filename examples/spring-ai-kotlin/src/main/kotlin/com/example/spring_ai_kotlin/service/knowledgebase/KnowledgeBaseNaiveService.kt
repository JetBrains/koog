package com.example.spring_ai_kotlin.service.knowledgebase

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.SimilaritySearchStrategy
import ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import org.springframework.stereotype.Service

@Service
class KnowledgeBaseNaiveService(
    private val promptExecutor: PromptExecutor,
    private val knowledgeBase: SearchStorage<TextDocument, SimilaritySearchRequest>
) {
    @OptIn(ExperimentalAgentsApi::class)
    suspend fun createAndRunAgent(userPrompt: String): String {
        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT5Nano,
            systemPrompt = """
                You are an internal knowledge base assistant.
                Answer only from the retrieved knowledge base context.
                If the answer is not supported by the retrieved context, say you do not know.
                """.trimIndent(),
            strategy = singleRunStrategy(),
            toolRegistry = ToolRegistry.EMPTY
        ) {
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

        return agent.run(
            userPrompt //"What is the process for requesting parental leave?"
        )
    }
}