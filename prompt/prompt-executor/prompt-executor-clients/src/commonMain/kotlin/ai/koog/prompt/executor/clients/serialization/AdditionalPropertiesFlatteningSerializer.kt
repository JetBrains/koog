package ai.koog.prompt.executor.clients.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * JSON serializer that handles additional properties in objects.
 *
 * On serialization: flattens `additionalProperties` to root level.
 * On deserialization: collects unknown properties into `additionalProperties` field.
 *
 * Compatible with `JsonNamingStrategy.SnakeCase`: field names are matched against both their
 * declared form and their snake_case form, and the additional-properties key is emitted in the
 * form that matches the payload's naming so the inner serializer can read it back.
 *
 * @param tSerializer Underlying serializer for type [T].
 * @param additionalPropertiesField The name of the field to use for additional properties, defaults to "additionalProperties".
 */
public abstract class AdditionalPropertiesFlatteningSerializer<T>(
    tSerializer: KSerializer<T>,
    private val additionalPropertiesField: String = "additionalProperties"
) :
    JsonTransformingSerializer<T>(tSerializer) {

    private val additionalPropertiesFieldSnakeCase: String = additionalPropertiesField.toSnakeCase()

    private val additionalPropertiesFieldCandidates: Set<String> =
        setOf(additionalPropertiesField, additionalPropertiesFieldSnakeCase)

    private val declaredNames: Set<String> = tSerializer.descriptor.elementNames.toSet()

    private val knownProperties: Set<String> = buildSet {
        declaredNames.forEach { name ->
            add(name)
            add(name.toSnakeCase())
        }
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject

        return buildJsonObject {
            // Add all properties except additionalProperties (in any naming form)
            obj.entries.asSequence()
                .filterNot { (key, _) -> key in additionalPropertiesFieldCandidates }
                .forEach { (key, value) -> put(key, value) }

            // Merge additional properties into the root level (avoiding conflicts)
            val sourceKey = additionalPropertiesFieldCandidates.firstOrNull(obj::containsKey)
            if (sourceKey != null) {
                (obj[sourceKey] as? JsonObject)?.entries
                    ?.filterNot { (key, _) -> obj.containsKey(key) }
                    ?.forEach { (key, value) -> put(key, value) }
            }
        }
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val (known, additional) = obj.entries.partition { (key, _) -> key in knownProperties }

        // If any recognized key is in snake_case form (i.e., not the declared name), the Json
        // instance is using a naming strategy, so emit the additional-properties key in the
        // same form for the inner serializer to pick it up.
        val usesSnakeCase = known.any { (key, _) -> key !in declaredNames }
        val outputKey = if (usesSnakeCase) additionalPropertiesFieldSnakeCase else additionalPropertiesField

        return buildJsonObject {
            // Add known properties efficiently
            known.forEach { (key, value) -> put(key, value) }

            // Group additional properties under an additionalProperties key if any exist
            if (additional.isNotEmpty()) {
                put(
                    outputKey,
                    buildJsonObject {
                        additional.forEach { (key, value) -> put(key, value) }
                    }
                )
            }
        }
    }
}

private fun String.toSnakeCase(): String {
    if (isEmpty()) return this
    val result = StringBuilder(length + 4)
    for (i in indices) {
        val c = this[i]
        if (c.isUpperCase()) {
            if (i > 0 && this[i - 1].isLowerCase()) result.append('_')
            result.append(c.lowercaseChar())
        } else {
            result.append(c)
        }
    }
    return result.toString()
}
