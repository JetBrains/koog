package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

public fun allModelsIn(obj: Any): List<LLModel> {
        return obj::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .filter { it.returnType == LLModel::class.createType() }
            .map { it.getter.call(obj) as LLModel }
    }

public fun LLModelDefinitions.list(): List<LLModel> {
    return allModelsIn(this)
}

