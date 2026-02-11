package ai.koog.protocol.parser

import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.agent.FlowDataType.FlowArrayBoolean
import ai.koog.protocol.agent.FlowDataType.FlowArrayDouble
import ai.koog.protocol.agent.FlowDataType.FlowArrayInteger
import ai.koog.protocol.agent.FlowDataType.FlowArrayString
import ai.koog.protocol.agent.FlowDataType.FlowBoolean
import ai.koog.protocol.agent.FlowDataType.FlowCritiqueResult
import ai.koog.protocol.agent.FlowDataType.FlowDouble
import ai.koog.protocol.agent.FlowDataType.FlowInteger
import ai.koog.protocol.agent.FlowDataType.FlowPrimitiveType
import ai.koog.protocol.agent.FlowDataType.FlowString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
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
 * Custom serializer for [FlowDataType] that handles polymorphic JSON deserialization.
 *
 * Supports deserializing:
 * - JSON primitives (string, int, double, boolean) -> [FlowString], [FlowInteger], [FlowDouble], [FlowBoolean]
 * - JSON arrays of primitives -> [FlowArrayString], [FlowArrayInteger], [FlowArrayDouble], [FlowArrayBoolean]
 * - JSON objects with {success, feedback, input} -> [FlowCritiqueResult]
 */
internal object FlowDataTypeSerializer : KSerializer<FlowDataType> {

    private val logger = KotlinLogging.logger { }

    // Use a primitive string descriptor to hide internal structure from tool schemas
    // The LLM will see this as accepting/returning a simple string value
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlowDataType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): FlowDataType {
        val jsonDecoder = decoder as? JsonDecoder ?: error("FlowDataType can only be deserialized from JSON")
        val element = jsonDecoder.decodeJsonElement()

        return element.toFlowDataType() ?: error("Cannot deserialize FlowDataType from: $element")
    }

    override fun serialize(encoder: Encoder, value: FlowDataType) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("FlowDataType can only be serialized to JSON")
        val element = value.toJsonElement()
        jsonEncoder.encodeJsonElement(element)
    }

    private fun JsonElement.toFlowDataType(): FlowDataType? {
        return when (this) {
            is JsonPrimitive -> toFlowPrimitiveType()
            is JsonArray -> toFlowArrayType()
            is JsonObject -> toFlowObjectType()
            JsonNull -> null
        }
    }

    private fun JsonPrimitive.toFlowPrimitiveType(): FlowDataType? {
        return booleanOrNull?.let { FlowBoolean(it) }
            ?: intOrNull?.let { FlowInteger(it) }
            ?: doubleOrNull?.let { FlowDouble(it) }
            ?: contentOrNull?.let { FlowString(it) }
    }

    private fun JsonArray.toFlowArrayType(): FlowDataType {
        if (isEmpty()) {
            // Default to an empty string array for empty arrays
            return FlowArrayString(emptyArray())
        }

        // Check the first element to determine the type, then validate the rest
        val firstElement = first()
        if (firstElement !is JsonPrimitive) {
            error("Expected an array of uniform primitive types, but got: $this")
        }

        return when {
            firstElement.isString -> {
                if (all { it is JsonPrimitive && it.isString }) {
                    FlowArrayString(Array(size) { (get(it) as JsonPrimitive).content })
                } else {
                    error("Expected an array of strings, but got mixed types: $this")
                }
            }
            firstElement.booleanOrNull != null -> {
                if (all { it is JsonPrimitive && it.booleanOrNull != null }) {
                    FlowArrayBoolean(Array(size) { (get(it) as JsonPrimitive).booleanOrNull!! })
                } else {
                    error("Expected an array of booleans, but got mixed types: $this")
                }
            }
            firstElement.intOrNull != null -> {
                if (all { it is JsonPrimitive && it.intOrNull != null }) {
                    FlowArrayInteger(Array(size) { (get(it) as JsonPrimitive).intOrNull!! })
                } else {
                    error("Expected an array of integers, but got mixed types: $this")
                }
            }
            firstElement.doubleOrNull != null -> {
                if (all { it is JsonPrimitive && it.doubleOrNull != null }) {
                    FlowArrayDouble(Array(size) { (get(it) as JsonPrimitive).doubleOrNull!! })
                } else {
                    error("Expected an array of doubles, but got mixed types: $this")
                }
            }
            else -> {
                error("Expected an array of uniform primitive types, but got: $this")
            }
        }
    }

    private fun JsonObject.toFlowObjectType(): FlowDataType? {
        val type = this["type"]?.jsonPrimitive?.contentOrNull
        if (type == null) {
            logger.warn { "Missing type field in result: $this" }
            return null
        }

        return when (type) {
            "boolean" -> {
                this["data"]?.jsonPrimitive?.booleanOrNull?.let { FlowBoolean(it) }
            }
            "string" -> {
                this["data"]?.jsonPrimitive?.contentOrNull?.let { FlowString(it) }
            }
            "int" -> {
                this["data"]?.jsonPrimitive?.intOrNull?.let { FlowInteger(it) }
            }
            "double" -> {
                this["data"]?.jsonPrimitive?.doubleOrNull?.let { FlowDouble(it) }
            }
            "array_boolean" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.booleanOrNull }?.toTypedArray()?.let {
                    FlowArrayBoolean(it)
                }
            }
            "array_string" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toTypedArray()?.let {
                    FlowArrayString(it)
                }
            }
            "array_int" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull }?.toTypedArray()?.let {
                    FlowArrayInteger(it)
                }
            }
            "array_double" -> {
                this["data"]?.jsonArray?.mapNotNull { it.jsonPrimitive.doubleOrNull }?.toTypedArray()?.let {
                    FlowArrayDouble(it)
                }
            }
            "critique" -> {
                val success = this["success"]?.jsonPrimitive?.booleanOrNull ?: return null
                val feedback = this["feedback"]?.jsonPrimitive?.contentOrNull ?: return null
                val input = this["input"]?.toFlowDataType() ?: return null

                FlowCritiqueResult(success, feedback, input)
            }

            else -> error("Unsupported input type: <$type>")
        }
    }

    private fun FlowDataType.toJsonElement(): JsonElement {
        return buildJsonObject {
            when (this@toJsonElement) {
                is FlowString -> {
                    put("type", "string")
                    put("data", JsonPrimitive((data)))
                }
                is FlowInteger -> {
                    put("type", "int")
                    put("data", JsonPrimitive((data)))
                }
                is FlowDouble -> {
                    put("type", "double")
                    put("data", JsonPrimitive((data)))
                }
                is FlowBoolean -> {
                    put("type", "boolean")
                    put("data", JsonPrimitive((data)))
                }
                is FlowArrayString -> {
                    put("type", "array_string")
                    put("data", buildJsonArray { data.forEach { add(JsonPrimitive(it)) } })
                }
                is FlowArrayInteger -> {
                    put("type", "array_int")
                    put("data", buildJsonArray { data.forEach { add(JsonPrimitive(it)) } })
                }
                is FlowArrayDouble -> {
                    put("type", "array_double")
                    put("data", buildJsonArray { data.forEach { add(JsonPrimitive(it)) } })
                }
                is FlowArrayBoolean -> {
                    put("type", "array_boolean")
                    put("data", buildJsonArray { data.forEach { add(JsonPrimitive(it)) } })
                }
                is FlowCritiqueResult -> {
                    put("type", "critique")
                    put("success", success)
                    put("feedback", feedback)
                    put("input", input.toJsonElement())
                }

                else -> error("Unsupported input type: $this")
            }
        }
    }
}

/**
 * Converts a JsonPrimitive to a Primitive FlowDataType type.
 */
public fun JsonPrimitive.toPrimitiveFlowDataType(): FlowPrimitiveType {
    return booleanOrNull?.let { FlowBoolean(it) }
        ?: intOrNull?.let { FlowInteger(it) }
        ?: doubleOrNull?.let { FlowDouble(it) }
        ?: contentOrNull?.let { FlowString(it) }
        ?: error("Unsupported primitive type: $this")
}
