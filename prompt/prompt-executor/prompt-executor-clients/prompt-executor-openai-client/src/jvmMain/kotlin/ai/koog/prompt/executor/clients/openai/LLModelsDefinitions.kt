package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.allModelsIn
import ai.koog.prompt.llm.LLModel

/**
 * Creates a combined list of all OpenAI Large Language Models ([LLModel]) available within the
 * predefined model categories in the [OpenAIModels] object. The categories include
 * Reasoning, Chat, CostOptimized, and Embeddings.
 *
 * @return A list of `LLModel` instances representing all available models across the categories.
 */
public fun OpenAIModels.list(): List<LLModel> {
    return buildList {
        addAll(allModelsIn(OpenAIModels.Reasoning))
        addAll(allModelsIn(OpenAIModels.Chat))
        addAll(allModelsIn(OpenAIModels.CostOptimized))
        addAll(allModelsIn(OpenAIModels.Embeddings))
    }
}
