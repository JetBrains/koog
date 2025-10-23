package ai.koog.agents.core.utils

import ai.koog.agents.core.annotation.InternalAgentsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import kotlin.reflect.KType

/**
 * Utility object for handling serialization of input data to JSON using Kotlin Serialization.
 */
@InternalAgentsApi
public object SerializationUtils {

    private val json = Json {
        prettyPrint = true
    }

    private val logger = KotlinLogging.logger { }

    /**
     * Serializes the given data to a string using the specified data type.
     * If serialization fails, falls back to [data.toString()].
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @param default A lambda function that returns a fallback string if serialization fails.
     * @return A [String] representing the serialized data, or the result of [data.toString()] if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToStringOrDefault(
        data: Any?,
        dataType: KType,
        json: Json? = null,
        default: (() -> String)? = null
    ): String =
        encodeDataToStringOrNull(data, dataType)
            ?: default?.invoke()
            ?: data.toString()

    /**
     * Serializes the given data to a string using the specified data type.
     * Returns the serialized string if successful, or null if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @return A [String] representing the serialized data, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToStringOrNull(data: Any?, dataType: KType, json: Json? = null): String? =
        try {
            encodeDataToString(data, dataType, json)
        } catch (e: SerializationException) {
            logger.debug { "Failed to serialize data to string: ${e.message}" }
            null
        }

    /**
     * Serializes the given data to a string using the specified data type.
     * Throws [SerializationException] if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @return A [String] representing the serialized data.
     * @throws SerializationException if serialization fails or no serializer is found for the data type.
     */
    @InternalAgentsApi
    public fun encodeDataToString(data: Any?, dataType: KType, json: Json? = null): String {
        val json = json ?: SerializationUtils.json
        val serializer = json.serializersModule.serializer(dataType)
        return json.encodeToString(serializer, data)
    }

    /**
     * Serializes the given data to a [JsonElement] using the specified data type.
     * If serialization fails, falls back to a [JsonPrimitive] wrapping [data.toString()].
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @param default A lambda function that returns a fallback [JsonElement] if serialization fails.
     * @return A [JsonElement] representing the serialized data, or a [JsonPrimitive] containing [data.toString()] if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElementOrDefault(
        data: Any?,
        dataType: KType,
        json: Json? = null,
        default: (() -> JsonElement)? = null
    ): JsonElement =
        encodeDataToJsonElementOrNull(data, dataType, json)
            ?: default?.invoke()
            ?: JsonPrimitive(data.toString())

    /**
     * Serializes the given data to a [JsonElement] using the specified data type.
     * Returns the serialized [JsonElement] if successful, or null if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @return A [JsonElement] representing the serialized data, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElementOrNull(data: Any?, dataType: KType, json: Json? = null): JsonElement? =
        try {
            encodeDataToJsonElement(data, dataType, json)
        } catch (e: SerializationException) {
            logger.debug { "Failed to serialize data to json element: ${e.message}" }
            null
        }

    /**
     * Serializes the given data to a [JsonElement] using the specified data type.
     * Throws [SerializationException] if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @return A [JsonElement] representing the serialized data.
     * @throws SerializationException if serialization fails or no serializer is found for the data type.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElement(data: Any?, dataType: KType, json: Json? = null): JsonElement {
        val json = json ?: SerializationUtils.json
        val serializer = json.serializersModule.serializer(dataType)
        return json.encodeToJsonElement(serializer, data)
    }

    /**
     * Attempts to parse the given string into a [JsonElement]. If the parsing fails due to a
     * [SerializationException], it falls back to returning a [JsonPrimitive] wrapping the original string.
     *
     * @param data The input string to be parsed into a [JsonElement].
     * @return A [JsonElement] if parsing succeeds; otherwise, a [JsonPrimitive] containing the original string.
     */
    @InternalAgentsApi
    public fun parseDataToJsonElementOrDefault(
        data: String,
        json: Json? = null,
        default: (() -> JsonElement)? = null
    ): JsonElement {
        logger.debug { "Parsing data to JsonElement: $data" }
        val json = json ?: SerializationUtils.json

        return try {
            json.parseToJsonElement(data)
        } catch (e: SerializationException) {
            logger.debug { "Failed to parse data to JsonElement: ${e.message}. Return JsonPrimitive instance" }
            default?.invoke() ?: JsonPrimitive(data)
        }
    }
}
