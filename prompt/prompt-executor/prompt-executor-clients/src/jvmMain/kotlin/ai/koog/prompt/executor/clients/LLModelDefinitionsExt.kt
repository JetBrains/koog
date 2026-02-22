package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel
import org.jetbrains.annotations.VisibleForTesting
import java.lang.reflect.Modifier
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberExtensionProperties
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField

/**
 * Retrieves all public properties of the specified object and nested objects that are of type `LLModel` and
 * returns them as a list.
 *
 * @param obj The object to inspect for properties of type `LLModel`.
 * @return A list of `LLModel` instances extracted from the public properties of the provided object
 *   and its nested objects.
 */
@VisibleForTesting
public fun allModelsIn(obj: Any): List<LLModel> {
    val immediateModels = (obj::class.memberProperties + obj::class.memberExtensionProperties)
        .filter { it.visibility == KVisibility.PUBLIC }
        .filter { it.returnType == LLModel::class.createType() }
        .map {
            // For @JvmField properties (public fields), access the field directly
            // Otherwise use the getter with receiver object
            val javaField = it.javaField
            if (javaField != null && Modifier.isPublic(javaField.modifiers)) {
                // For object singletons with @JvmField, fields are static, so pass null
                javaField.get(null) as LLModel
            } else {
                it.getter.call(obj) as LLModel
            }
        }

    val nestedModels = obj::class.nestedClasses
        .mapNotNull { it.objectInstance }
        .flatMap { allModelsIn(it) }

    return immediateModels + nestedModels
}

/**
 * Retrieves a list of all `LLModel` instances defined within the current `LLModelDefinitions`.
 *
 * The method scans for all publicly visible properties of the `LLModelDefinitions` instance,
 * identifies those which return an `LLModel`, and compiles them into a list. This allows
 * easy access to all available model definitions in a given context.
 * @param customModels A list of additional `LLModel` instances to include in the list.
 *
 * @return A list of `LLModel` instances representing all models defined in this `LLModelDefinitions`.
 */
@VisibleForTesting
public fun LLModelDefinitions.list(customModels: List<LLModel> = emptyList()): List<LLModel> {
    return allModelsIn(this) + customModels
}
