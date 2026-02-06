package ai.koog.protocol.parser

import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.agent.InputArrayBoolean
import ai.koog.protocol.agent.InputArrayDouble
import ai.koog.protocol.agent.InputArrayInt
import ai.koog.protocol.agent.InputArrayString
import ai.koog.protocol.agent.InputBoolean
import ai.koog.protocol.agent.InputCritiqueResult
import ai.koog.protocol.agent.InputDouble
import ai.koog.protocol.agent.InputInt
import ai.koog.protocol.agent.InputString
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
import kotlinx.serialization.json.jsonArray
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
        val jsonDecoder = decoder as? JsonDecoder ?: error("FlowAgentInput can only be deserialized from JSON")
        val element = jsonDecoder.decodeJsonElement()

        return element.toFlowAgentInput() ?: error("Cannot deserialize FlowAgentInput from: $element")
    }

    override fun serialize(encoder: Encoder, value: FlowAgentInput) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("FlowAgentInput can only be serialized to JSON")
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
            return InputString(content)
        }

        return booleanOrNull?.let { InputBoolean(it) }
            ?: intOrNull?.let { InputInt(it) }
            ?: doubleOrNull?.let { InputDouble(it) }
            ?: InputString(content)
    }

    private fun JsonArray.toFlowAgentInputArray(): FlowAgentInput {
        if (isEmpty()) {
            // Default to empty string array for empty arrays
            return InputArrayString(emptyArray())
        }

        return when {
            all { it is JsonPrimitive && it.isString } -> {
                InputArrayString(mapNotNull { it.jsonPrimitive.contentOrNull }.toTypedArray())
            }
            all { it is JsonPrimitive && it.booleanOrNull != null } -> {
                InputArrayBoolean(mapNotNull { it.jsonPrimitive.booleanOrNull }.toTypedArray())
            }
            all { it is JsonPrimitive && it.intOrNull != null } -> {
                InputArrayInt(mapNotNull { it.jsonPrimitive.intOrNull }.toTypedArray())
            }
            all { it is JsonPrimitive && it.doubleOrNull != null } -> {
                InputArrayDouble(mapNotNull { it.jsonPrimitive.doubleOrNull }.toTypedArray())
            }
            else -> {
                error("Expected an array of uniform primitive types, but got: $this")
            }
        }
    }

    private fun JsonObject.toFlowAgentInputObject(): FlowAgentInput? {

        // Try to parse as InputCritiqueResult
        val success = this["success"]?.jsonPrimitive?.booleanOrNull
        val feedback = this["feedback"]?.jsonPrimitive?.contentOrNull
        val input = this["input"]?.toFlowAgentInput()

        if (success != null && feedback != null && input != null) {
            return InputCritiqueResult(success, feedback, input)
        }

        val type = this["type"]?.jsonPrimitive?.contentOrNull ?: return null

        return when (type) {
            "boolean" -> {
                this["data"]?.jsonPrimitive?.booleanOrNull?.let { InputBoolean(it) }
            }
            "string" -> {
                this["data"]?.jsonPrimitive?.contentOrNull?.let { InputString(it) }
            }
            "int" -> {
                this["data"]?.jsonPrimitive?.intOrNull?.let { InputInt(it) }
            }
            "double" -> {
                this["data"]?.jsonPrimitive?.doubleOrNull?.let { InputDouble(it) }
            }
            "array_boolean" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.booleanOrNull }?.toTypedArray()?.let {
                    InputArrayBoolean(it)
                }
            }
            "array_string" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toTypedArray()?.let {
                    InputArrayString(it)
                }
            }
            "array_int" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toTypedArray()?.let {
                    InputArrayInt(it)
                }
            }
            "array_double" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }?.toTypedArray()?.let {
                    InputArrayDouble(it)
                }
            }

            else -> error("Unsupported input type: <$type>")
        }
    }

    private fun FlowAgentInput.toJsonElement(): JsonElement {
        return buildJsonObject {
            when (this@toJsonElement) {
                is InputString -> {
                    put("type", "string")
                    put("data", JsonPrimitive((data)))
                }
                is InputInt -> {
                    put("type", "int")
                    put("data", JsonPrimitive((data)))
                }
                is InputDouble -> {
                    put("type", "double")
                    put("data", JsonPrimitive((data)))
                }
                is InputBoolean -> {
                    put("type", "boolean")
                    put("data", JsonPrimitive((data)))
                }
                is InputArrayString -> {
                    put("type", "array_string")
                    put("data",
                        buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
                    )
                }
                is InputArrayInt -> {
                    put("type", "array_int")
                    put("data",
                        buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
                    )
                }
                is InputArrayDouble -> {
                    put("type", "array_double")
                    put("data",
                        buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
                    )
                }
                is InputArrayBoolean -> {
                    put("type", "array_boolean")
                    put("data",
                        buildJsonArray { data.forEach { add(JsonPrimitive(it)) } }
                    )
                }
                is InputCritiqueResult -> {
                    buildJsonObject {
                        put("type", "critique")
                        put("success", success)
                        put("feedback", feedback)
                        put("input", input.toJsonElement())
                    }
                }

                else -> error("Unsupported input type: $this")
            }
        }
    }
}
