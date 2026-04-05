package com.example.spring_ai_kotlin.service.deepresearch

import com.example.spring_ai_kotlin.service.deepresearch.model.ResearchState
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import org.springframework.stereotype.Service

@Service
class DeepLiteratureResearchService (
    private val promptExecutor: PromptExecutor
) {

    suspend fun createAndRunAgent(userPrompt: String): String {
        val agent = buildResearchAgent(promptExecutor, OpenAIModels.Chat.GPT5Nano)
        val initialState = ResearchState(topic = userPrompt)
        val result = agent.run(initialState)

        return result.finalSummary ?: "No final report generated."
    }
}