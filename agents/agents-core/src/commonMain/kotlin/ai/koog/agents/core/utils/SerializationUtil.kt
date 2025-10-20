package ai.koog.agents.core.utils

import ai.koog.agents.core.annotation.InternalAgentsApi
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KType

/**
 * Utility object for handling serialization of input data to JSON using Kotlin Serialization.
 */
@InternalAgentsApi
public object SerializationUtil {

    private val json = Json {
        prettyPrint = true
    }

    private val logger = KotlinLogging.logger { }

    /**
     * Attempts to serialize the given input data using the provided data type.
     * Returns the serialized JsonElement if successful, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun trySerializeDataToJsonElement(data: Any?, dataType: KType): JsonElement? =
        try {
            serializeDataToJsonElement(data, dataType)
        } catch (e: SerializationException) {
            logger.debug { "Failed to serialize data: ${e.message}" }
            null
        }

    /**
     * Serializes the given input data into a JSON element using the specified data type.
     * If no serializer is found for the data type, the function returns null.
     *
     * @param data The object to be serialized.
     * @param dataType The type of the object used to find the appropriate serializer.
     * @return A JsonElement representing the serialized data, or null if serialization fails.
     */
    @InternalAgentsApi
    public fun serializeDataToJsonElement(data: Any?, dataType: KType): JsonElement? {
        val serializer = json.serializersModule.serializerOrNull(dataType)
        if (serializer == null) {
            logger.debug { "No serializer found for data type: $dataType" }
            return null
        }

        return json.encodeToJsonElement(serializer, data)
    }
}
