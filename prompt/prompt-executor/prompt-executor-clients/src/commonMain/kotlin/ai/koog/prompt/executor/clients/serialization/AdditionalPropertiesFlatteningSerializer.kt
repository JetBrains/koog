package ai.koog.prompt.executor.clients.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * JSON serializer that handles additional properties in objects.
 *
 * On serialization: flattens `additionalProperties` to root level.
 * On deserialization: collects unknown properties into `additionalProperties` field.
 *
 * @param knownProperties Set of known property names for the type
 */
public abstract class AdditionalPropertiesFlatteningSerializer<T>(tSerializer: KSerializer<T>) :
    JsonTransformingSerializer<T>(tSerializer) {

    private val additionalPropertiesField = "additionalProperties"

    private val knownProperties = tSerializer.descriptor.elementNames

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject

        return buildJsonObject {
            // Add all properties except additionalProperties
            obj.entries.asSequence()
                .filterNot { (key, _) -> key == additionalPropertiesField }
                .forEach { (key, value) -> put(key, value) }

            // Merge additional properties into the root level (avoiding conflicts)
            obj[additionalPropertiesField]?.jsonObject?.entries
                ?.filterNot { (key, _) -> obj.containsKey(key) }
                ?.forEach { (key, value) -> put(key, value) }
        }
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element.jsonObject
        val (known, additional) = obj.entries.partition { (key, _) -> key in knownProperties }

        return buildJsonObject {
            // Add known properties efficiently
            known.forEach { (key, value) -> put(key, value) }

            // Group additional properties under an additionalProperties key if any exist
            if (additional.isNotEmpty()) {
                put(
                    additionalPropertiesField,
                    buildJsonObject {
                        additional.forEach { (key, value) -> put(key, value) }
                    }
                )
            }
        }
    }
}
