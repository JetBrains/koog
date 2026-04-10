package com.example.spring_ai_kotlin.service.knowledgebase

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.TextDocument
import ai.koog.rag.base.storage.SearchStorage
import ai.koog.rag.base.storage.search.SimilaritySearchRequest
import org.springframework.stereotype.Service

@Service
class KnowledgeBaseAgenticService(
    private val promptExecutor: PromptExecutor,
    private val knowledgeBase: SearchStorage<TextDocument, SimilaritySearchRequest>
) {
    @Tool
    @LLMDescription("Search the knowledge base for documents relevant to a query. Returns the content of the most relevant documents.")
    suspend fun searchKnowledgeBase(
        @LLMDescription("The search query describing what information you need")
        query: String,
        @LLMDescription("Maximum number of documents to return")
        count: Int
    ): String {
        val results = knowledgeBase.search(SimilaritySearchRequest(queryText = query, limit = count, minScore = 0.5))

        if (results.isEmpty()) {
            return "No relevant documents found for: $query"
        }

        val response = StringBuilder("Found ${results.size} relevant documents:\n\n")
        results.forEachIndexed { index, result ->
            response.append("Document ${index + 1}: ${result.document.id}")
            response.append(" (score: ${"%.2f".format(result.score.value)})\n")
            response.append("Content: ${result.document}\n\n")
        }
        return response.toString()
    }

    @OptIn(ExperimentalAgentsApi::class)
    suspend fun createAndRunAgent(userPrompt: String): String {
        val toolRegistry = ToolRegistry {
            tool(::searchKnowledgeBase)
        }

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT5Nano,
            systemPrompt = """
                You are an internal knowledge base assistant.
                Answer only from the retrieved knowledge base context.
                If the answer is not supported by the retrieved context, say you do not know.
                """.trimIndent(),
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry
        )

        return agent.run(
            userPrompt //"What is the process for requesting parental leave?"
        )
    }
}
