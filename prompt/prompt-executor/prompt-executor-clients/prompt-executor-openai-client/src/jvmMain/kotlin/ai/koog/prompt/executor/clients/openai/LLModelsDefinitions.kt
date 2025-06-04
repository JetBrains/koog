package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.allModelsIn
import ai.koog.prompt.llm.LLModel

public fun OpenAIModels.list(): List<LLModel> {
    return buildList {
        addAll(allModelsIn(OpenAIModels.Reasoning))
        addAll(allModelsIn(OpenAIModels.Chat))
        addAll(allModelsIn(OpenAIModels.CostOptimized))
        addAll(allModelsIn(OpenAIModels.Embeddings))
    }
}
