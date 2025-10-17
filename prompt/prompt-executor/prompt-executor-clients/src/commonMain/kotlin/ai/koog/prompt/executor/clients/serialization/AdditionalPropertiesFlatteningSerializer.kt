package ai.koog.prompt.executor.clients.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
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
private class AdditionalPropertiesFlatteningSerializer<T>(
    tSerializer: KSerializer<T>,
    private val json: Json
) :
    JsonTransformingSerializer<T>(tSerializer) {

    private val additionalPropertiesField = "additionalProperties"

    private val knownProperties =
        if (json.configuration.namingStrategy == null) {
            tSerializer.descriptor.elementNames
        } else {
            tSerializer.descriptor.elementNames.mapIndexed { index, string ->
                json.configuration.namingStrategy!!.serialNameForJson(tSerializer.descriptor, index, string)
            }
        }

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

/**
 * Wrap AdditionalPropertiesFlatteningSerializer
 */
public open class AdditionalPropertiesSerializer<T>(
    private val baseSerializer: KSerializer<T>
): KSerializer<T> {
    override val descriptor: SerialDescriptor
        get() = baseSerializer.descriptor

    override fun serialize(encoder: Encoder, value: T) {
        if (encoder is JsonEncoder) {
            val json = encoder.json
            val delegate = AdditionalPropertiesFlatteningSerializer(baseSerializer, json)
            delegate.serialize(encoder, value)
        } else {
            baseSerializer.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): T {
        if (decoder is JsonDecoder) {
            val json = decoder.json
            val delegate = AdditionalPropertiesFlatteningSerializer(baseSerializer, json)
            return delegate.deserialize(decoder)
        }
        return baseSerializer.deserialize(decoder)
    }

}


