package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

/**
 * Retrieves all public properties of the specified object that are of type `LLModel` and returns them as a list.
 *
 * @param obj The object to inspect for properties of type `LLModel`.
 * @return A list of `LLModel` instances extracted from the public properties of the provided object.
 */
public fun allModelsIn(obj: Any): List<LLModel> {
        return obj::class.memberProperties
            .filter { it.visibility == KVisibility.PUBLIC }
            .filter { it.returnType == LLModel::class.createType() }
            .map { it.getter.call(obj) as LLModel }
    }

/**
 * Retrieves a list of all `LLModel` instances defined within the current `LLModelDefinitions`.
 *
 * The method scans for all publicly visible properties of the `LLModelDefinitions` instance,
 * identifies those which return an `LLModel`, and compiles them into a list. This allows
 * easy access to all available model definitions in a given context.
 *
 * @return A list of `LLModel` instances representing all models defined in this `LLModelDefinitions`.
 */
public fun LLModelDefinitions.list(): List<LLModel> {
    return allModelsIn(this)
}

