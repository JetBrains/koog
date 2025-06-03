package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.llm.LLModel
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

internal fun allModelsIn(obj: Any): List<LLModel> {
        return obj::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .filter { it.returnType == LLModel::class.createType() }
            .map { it.getter.call(obj) as LLModel }
    }

public fun OpenAIModels.list(): List<LLModel> {
    return buildList {
        addAll(allModelsIn(OpenAIModels.Reasoning))
        addAll(allModelsIn(OpenAIModels.Chat))
        addAll(allModelsIn(OpenAIModels.CostOptimized))
        addAll(allModelsIn(OpenAIModels.Embeddings))
    }
}
