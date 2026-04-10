package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.executor.clients.LLMClientException
import com.google.genai.types.Schema
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Stateless utility methods for JSON/Map bidirectional conversion, SDK [Schema] conversion,
 * and thought-signature encoding used across the Google GenAI client converters.
 */
internal object GoogleGenaiConversionUtils {

    // region Signature encoding

    fun signatureToBytes(value: String): ByteArray = Base64.getDecoder().decode(value)
    fun signatureFromBytes(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    // endregion

    // region JSON string -> Map

    /**
     * Parses a JSON string (tool call args) into a Map<String, Object> for the SDK.
     *
     * @throws LLMClientException if the JSON string cannot be parsed.
     */
    fun parseJsonToMap(jsonString: String, clientName: String): Map<String, Any?> {
        if (jsonString.isBlank() || jsonString == "{}") return emptyMap()
        return try {
            val element = Json.parseToJsonElement(jsonString)
            if (element is JsonObject) jsonObjectToMap(element) else emptyMap()
        } catch (e: SerializationException) {
            throw LLMClientException(
                clientName = clientName,
                message = "Failed to parse tool call JSON args: $jsonString",
                cause = e
            )
        }
    }

    // endregion

    // region JsonObject <-> Map

    /**
     * Converts a [JsonObject] to a plain Map for the SDK.
     */
    fun jsonObjectToMap(json: JsonObject): Map<String, Any?> {
        return json.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
        is JsonNull -> null

        is JsonPrimitive -> when {
            element.isString -> element.content
            element.content == "true" || element.content == "false" -> element.content.toBoolean()
            else -> element.content.toLongOrNull() ?: element.content.toDoubleOrNull() ?: element.content
        }

        is JsonObject -> jsonObjectToMap(element)

        is JsonArray -> element.map { jsonElementToAny(it) }
    }

    /**
     * Converts a Map<String, Object> from SDK response to a [JsonObject].
     */
    fun convertMapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        for ((key, value) in map) {
            when (value) {
                null -> put(key, JsonNull)

                is String -> put(key, value)

                is Int -> put(key, value.toLong())

                is Long -> put(key, value)

                is Number -> put(key, value.toDouble())

                is Boolean -> put(key, value)

                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    put(key, convertMapToJsonObject(value as Map<String, Any?>))
                }

                is List<*> -> {
                    put(key, JsonArray(value.map { convertAnyToJsonElement(it) }))
                }

                else -> put(key, value.toString())
            }
        }
    }

    private fun convertAnyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull

        is String -> JsonPrimitive(value)

        is Int -> JsonPrimitive(value.toLong())

        is Long -> JsonPrimitive(value)

        is Number -> JsonPrimitive(value.toDouble())

        is Boolean -> JsonPrimitive(value)

        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            convertMapToJsonObject(value as Map<String, Any?>)
        }

        is List<*> -> JsonArray(value.map { convertAnyToJsonElement(it) })

        else -> JsonPrimitive(value.toString())
    }

    // endregion

    // region SDK Schema conversion

    /**
     * Converts a [JsonObject] to an SDK [Schema] for response schema.
     */
    fun jsonObjectToSdkSchema(json: JsonObject): Schema {
        return Schema.fromJson(json.toString())
    }

    // endregion
}
