package ai.koog.agents.core.utils

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import ai.koog.serialization.typeToken
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
        allowStructuredMapKeys = true
    }

    private val logger = KotlinLogging.logger { }

    /**
     * Serializes the given data to a string using the specified data type.
     * If serialization fails, it falls back to [data.toString()].
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @param default A lambda function that returns a fallback string if serialization fails.
     *
     * @return A [String] representing the serialized data, or the result of [data.toString()] if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToStringOrDefault(
        data: Any?,
        dataType: TypeToken,
        serializer: JSONSerializer = KotlinxSerializer(),
        default: (() -> String)? = null
    ): String =
        encodeDataToStringOrNull(data, dataType, serializer)
            ?: default?.invoke()
            ?: data.toString()

    /**
     * Serializes the given data to a string using the specified data type.
     * Returns the serialized string if successful, or null if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [String] representing the serialized data, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToStringOrNull(
        data: Any?,
        dataType: TypeToken,
        serializer: JSONSerializer = KotlinxSerializer(),
    ): String? =
        try {
            encodeDataToString(data, dataType, serializer)
        } catch (e: IllegalArgumentException) {
            logger.debug { "Failed to serialize data to string: ${e.message}" }
            null
        }

    /**
     * Serializes the given data to a string using the specified data type.
     * Throws [SerializationException] if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [String] representing the serialized data.
     * @throws [SerializationException] if serialization fails or no serializer is found for the data type.
     * @throws [IllegalArgumentException] if no serializer is found for the specified data type.
     */
    @InternalAgentsApi
    public fun encodeDataToString(
        data: Any?,
        dataType: TypeToken,
        serializer: JSONSerializer = KotlinxSerializer(),
    ): String {
        return serializer.encodeToString(data, dataType)
    }

    /**
     * Serializes the given data into a string using the specified data type and optionally a custom Json instance.
     *
     * @param data The object to be serialized. Can be null.
     * @param dataType The [KType] representing the type of the object to determine the appropriate serializer.
     * @param json An optional [Json] instance to customize serialization. Defaults to [Json.Default] if not provided.
     * @return A [String] representation of the serialized data.
     * @throws SerializationException If the serialization process fails or a serializer cannot be found for the given type.
     * @throws IllegalArgumentException If no suitable serializer is available for the specified data type.
     */
    @InternalAgentsApi
    public fun encodeDataToString(
        data: Any?,
        dataType: KType,
        json: Json? = null,
    ): String = encodeDataToString(
        data,
        typeToken(dataType),
        json?.let { KotlinxSerializer(it) } ?: KotlinxSerializer()
    )

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
        dataType: TypeToken,
        serializer: JSONSerializer,
        default: (() -> JsonElement)? = null
    ): JsonElement =
        encodeDataToJsonElementOrNull(data, dataType, serializer)
            ?: default?.invoke()
            ?: JsonPrimitive(data.toString())

    /**
     * Serializes the given data to a [JsonElement] using the specified data type.
     * Returns the serialized [JsonElement] if successful, or null if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [JsonElement] representing the serialized data, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElementOrNull(
        data: Any?,
        dataType: TypeToken,
        serializer: JSONSerializer
    ): JsonElement? =
        try {
            encodeDataToJsonElement(data, dataType, serializer)
        } catch (e: IllegalArgumentException) {
            logger.debug { "Failed to serialize data to json element: ${e.message}" }
            null
        }

    /**
     * Serializes the provided data into a [JsonElement] or returns null if serialization fails.
     * This function uses a combination of the specified type and an optional [Json] serializer
     * to perform the conversion.
     *
     * @param data The object to be serialized, which can be null.
     * @param dataType The Kotlin type of the object, used to locate an appropriate serializer.
     * @param json An optional [Json] instance to use for serialization. If not provided, a default serializer is used.
     *
     * @return A [JsonElement] representing the serialized data, or null if the serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElementOrNull(
        data: Any?,
        dataType: KType,
        json: Json? = null
    ): JsonElement? = encodeDataToJsonElementOrNull(
        data,
        typeToken(dataType),
        json?.let { KotlinxSerializer(it) } ?: KotlinxSerializer()
    )

    /**
     * Serializes the given data to a [JsonElement] using the specified data type.
     * Throws [SerializationException] if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [JsonElement] representing the serialized data.
     * @throws [SerializationException] if serialization fails or no serializer is found for the data type.
     * @throws [IllegalArgumentException] if no serializer is found for the specified data type.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElement(
        data: Any?,
        dataType: TypeToken,
        serializer: JSONSerializer
    ): JsonElement {
        return serializer.encodeToJSONElement(data, dataType).toKotlinxJsonElement()
    }

    /**
     * Serializes the given data into a [JsonElement] using the specified type and optional JSON configuration.
     *
     * @param data The object to be serialized. This can be null if the data represents a nullable type.
     * @param dataType The Kotlin runtime type ([KType]) of the data, used to find the appropriate serializer.
     * @param json An optional [Json] instance to be used for serialization. If null, the default [Json] instance is used.
     *
     * @return A [JsonElement] that represents the serialized form of the input data.
     * @throws SerializationException If serialization fails or no serializer is found for the provided data type.
     * @throws IllegalArgumentException If the data type does not have a corresponding serializer.
     */
    @InternalAgentsApi
    public fun encodeDataToJsonElement(
        data: Any?,
        dataType: KType,
        json: Json? = null
    ): JsonElement = encodeDataToJsonElement(
        data,
        typeToken(dataType),
        json?.let { KotlinxSerializer(it) } ?: KotlinxSerializer()
    )

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
