package ai.koog.agents.core.utils

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.serialization.JSONElement
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.TypeToken
import io.github.oshai.kotlinlogging.KotlinLogging

/*
/**
 * Utility object for handling serialization of input data to JSON using Kotlin Serialization.
 */
@InternalAgentsApi
public object SerializationUtils {
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
        serializer: JSONSerializer,
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
    public fun encodeDataToStringOrNull(data: Any?, dataType: TypeToken, serializer: JSONSerializer): String? =
        try {
            encodeDataToString(data, dataType, serializer)
        } catch (e: IllegalArgumentException) {
            logger.debug { "Failed to serialize data to string: ${e.message}" }
            null
        }

    /**
     * Serializes the given data to a string using the specified data type.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [String] representing the serialized data.
     * @throws [IllegalArgumentException] if no serializer is found for the specified data type.
     */
    @InternalAgentsApi
    public fun encodeDataToString(data: Any?, dataType: TypeToken, serializer: JSONSerializer): String {
        return serializer.encodeToString(data, dataType)
    }

    /**
     * Serializes the given data to a [JSONElement] using the specified data type.
     * If serialization fails, falls back to a [JSONPrimitive] wrapping [data.toString()].
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @param default A lambda function that returns a fallback [JSONElement] if serialization fails.
     * @return A [JSONElement] representing the serialized data, or a [JSONPrimitive] containing [data.toString()] if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToJSONElementOrDefault(
        data: Any?,
        dataType: TypeToken,
        serializer: JSONSerializer,
        default: (() -> JSONElement)? = null
    ): JSONElement =
        encodeDataToJSONElementOrNull(data, dataType, serializer)
            ?: default?.invoke()
            ?: JSONPrimitive(data.toString())

    /**
     * Serializes the given data to a [JSONElement] using the specified data type.
     * Returns the serialized [JSONElement] if successful, or null if serialization fails.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [JSONElement] representing the serialized data, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun encodeDataToJSONElementOrNull(data: Any?, dataType: TypeToken, serializer: JSONSerializer): JSONElement? =
        try {
            encodeDataToJSONElement(data, dataType, serializer)
        } catch (e: IllegalArgumentException) {
            logger.debug { "Failed to serialize data to json element: ${e.message}" }
            null
        }

    /**
     * Serializes the given data to a [JSONElement] using the specified data type.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     *
     * @return A [JSONElement] representing the serialized data.
     * @throws [IllegalArgumentException] if no serializer is found for the specified data type.
     */
    @InternalAgentsApi
    public fun encodeDataToJSONElement(data: Any?, dataType: TypeToken, serializer: JSONSerializer): JSONElement {
        return serializer.encodeToJSONElement(data, dataType)
    }
}


 */
