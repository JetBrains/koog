package ai.koog.protocol.parser

import ai.koog.protocol.agent.FlowAgentInput
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Custom serializer for FlowAgentInput that handles polymorphic JSON deserialization.
 *
 * Supports deserializing:
 * - JSON primitives (string, int, double, boolean) -> InputString, InputInt, InputDouble, InputBoolean
 * - JSON arrays of primitives -> InputArrayStrings, InputArrayInt, InputArrayDouble, InputArrayBooleans
 * - JSON objects with {success, feedback, input} -> InputCritiqueResult
 */
internal object FlowAgentInputSerializer : KSerializer<FlowAgentInput> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): FlowAgentInput {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("FlowAgentInput can only be deserialized from JSON")
        val element = jsonDecoder.decodeJsonElement()
        return element.toFlowAgentInput()
            ?: error("Cannot deserialize FlowAgentInput from: $element")
    }

    override fun serialize(encoder: Encoder, value: FlowAgentInput) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("FlowAgentInput can only be serialized to JSON")
        val element = value.toJsonElement()
        jsonEncoder.encodeJsonElement(element)
    }

    private fun JsonElement.toFlowAgentInput(): FlowAgentInput? {
        return when (this) {
            is JsonPrimitive -> toFlowAgentInputPrimitive()
            is JsonArray -> toFlowAgentInputArray()
            is JsonObject -> toFlowAgentInputObject()
            JsonNull -> null
        }
    }

    private fun JsonPrimitive.toFlowAgentInputPrimitive(): FlowAgentInput {
        // Check isString first to preserve string types even for numeric-looking strings
        if (isString) {
            return FlowAgentInput.InputString(content)
        }
        return booleanOrNull?.let { FlowAgentInput.InputBoolean(it) }
            ?: intOrNull?.let { FlowAgentInput.InputInt(it) }
            ?: doubleOrNull?.let { FlowAgentInput.InputDouble(it) }
            ?: FlowAgentInput.InputString(content)
    }

    private fun JsonArray.toFlowAgentInputArray(): FlowAgentInput {
        if (isEmpty()) {
            // Default to empty string array for empty arrays
            return FlowAgentInput.InputArrayStrings(emptyArray())
        }

        return when {
            all { it is JsonPrimitive && it.isString } -> {
                FlowAgentInput.InputArrayStrings(mapNotNull { it.jsonPrimitive.contentOrNull }.toTypedArray())
            }
            all { it is JsonPrimitive && it.booleanOrNull != null } -> {
                FlowAgentInput.InputArrayBooleans(mapNotNull { it.jsonPrimitive.booleanOrNull }.toTypedArray())
            }
            all { it is JsonPrimitive && it.intOrNull != null } -> {
                FlowAgentInput.InputArrayInt(mapNotNull { it.jsonPrimitive.intOrNull }.toTypedArray())
            }
            all { it is JsonPrimitive && it.doubleOrNull != null } -> {
                FlowAgentInput.InputArrayDouble(mapNotNull { it.jsonPrimitive.doubleOrNull }.toTypedArray())
            }
            else -> {
                error("Expected an array of uniform primitive types, but got: $this")
            }
        }
    }

    private fun JsonObject.toFlowAgentInputObject(): FlowAgentInput {
        // Try to parse as InputCritiqueResult
        val success = this["success"]?.jsonPrimitive?.booleanOrNull
        val feedback = this["feedback"]?.jsonPrimitive?.contentOrNull
        val input = this["input"]?.toFlowAgentInput()

        if (success != null && feedback != null && input != null) {
            return FlowAgentInput.InputCritiqueResult(success, feedback, input)
        }

        error("Unable to deserialize FlowAgentInput from JSON object: $this")
    }

    private fun FlowAgentInput.toJsonElement(): JsonElement {
        return when (this) {
            is FlowAgentInput.InputString -> JsonPrimitive(data)
            is FlowAgentInput.InputInt -> JsonPrimitive(data)
            is FlowAgentInput.InputDouble -> JsonPrimitive(data)
            is FlowAgentInput.InputBoolean -> JsonPrimitive(data)
            is FlowAgentInput.InputArrayStrings -> buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
            is FlowAgentInput.InputArrayInt -> buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
            is FlowAgentInput.InputArrayDouble -> buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
            is FlowAgentInput.InputArrayBooleans -> buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
            is FlowAgentInput.InputCritiqueResult -> buildJsonObject {
                put("success", success)
                put("feedback", feedback)
                put("input", input.toJsonElement())
            }
        }
    }
}
