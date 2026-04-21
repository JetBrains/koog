package ai.koog.test.utils

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies that the given JSON payload can be correctly deserialized into an object of type [T],
 * and ensures that re-serializing the deserialized object produces the same JSON structure as the original payload.
 *
 * @param payload The JSON string to deserialize and verify.
 * @param serializationStrategy The serialization strategy to transform the object of type [T] into JSON.
 *      Defaults to the serializer for [T].
 * @param deserializationStrategy The deserialization strategy to transform the JSON into an object of type [T].
 *      Defaults to the serializer for [T].
 * @param json The [Json] instance used for serialization and deserialization processes. Defaults to [Json].
 * @return The deserialized object of type [T].
 * @throws IllegalArgumentException If the [payload] is not valid JSON.
 * @throws AssertionError If deserialization or re-serialization does not match the original [payload].
 */
@OptIn(InternalSerializationApi::class)
public inline fun <reified T : Any> verifyDeserialization(
    payload: String,
    serializationStrategy: SerializationStrategy<T> = T::class.serializer(),
    deserializationStrategy: DeserializationStrategy<T> = T::class.serializer(),
    json: Json = Json
): T {
    val payloadJsonElement = runCatching { json.parseToJsonElement(payload) }.getOrNull()

    requireNotNull(payloadJsonElement) { "Payload should be valid JSON: ```\n$payload\n```" }

    val model: T = json.decodeFromString(deserializationStrategy, payload)

    assertNotNull(model) {
        "JSON Payload could not be deserialized to ${T::class.simpleName}: ```\n$payload\n```"
    }

    val encoded = json.encodeToString(serializationStrategy, model)

    assertNotNull(encoded) {
        val jsonElement = json.parseToJsonElement(encoded)
        assertEquals(
            expected = payloadJsonElement,
            actual = jsonElement,
            message = "Deserialized model should generate original payload"
        )
    }

    return model
}

/**
 * Verifies that a given [payload] can be deserialized into the specified type [T] using the supplied
 * or default serializer, and ensures that re-serializing the deserialized object results
 * in the same JSON representation as the original payload.
 *
 * @param payload The JSON string to be deserialized and verified.
 * @param json The [Json] instance used for serialization and deserialization. Defaults to [Json].
 * @param serializer The [KSerializer] used for deserialization and serialization.
 *      Defaults to the serializer for type [T].
 * @return The deserialized object of type [T].
 * @throws IllegalArgumentException If the [payload] cannot be parsed as valid JSON.
 * @throws AssertionError If deserialization or re-serialization does not align with the original [payload].
 */
@OptIn(InternalSerializationApi::class)
public inline fun <reified T : Any> verifyDeserialization(
    payload: String,
    json: Json = Json,
    serializer: KSerializer<T> = T::class.serializer(),
): T = verifyDeserialization(
    payload = payload,
    serializationStrategy = serializer,
    deserializationStrategy = serializer,
    json = json
)

/**
 * Shared JSON configuration for extended serialization tests that need to encode defaults.
 * Useful for tests that verify all fields are properly serialized.
 */
public val testJson: Json = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Shared JSON configuration with ignoreUnknownKeys = true.
 * Used for parameterized tests that need to handle unknown properties.
 */
public val testJsonIgnoreUnknown: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * Provides both strict and lenient JSON configurations for parameterized testing.
 * Returns a list of [Json, String] pairs where the string describes the configuration.
 */
public fun testJsonConfigurations(): List<Pair<Json, String>> = listOf(
    testJson to "strict (ignoreUnknownKeys=false)",
    testJsonIgnoreUnknown to "lenient (ignoreUnknownKeys=true)"
)

/**
 * Runs a test with both JSON configurations (ignoreUnknownKeys = true/false).
 * This is a helper for parameterized testing that ensures each test runs with both configurations.
 */
public fun runWithBothJsonConfigurations(testName: String, test: (Json) -> Unit) {
    testJsonConfigurations().forEach { (json, description) ->
        try {
            test(json)
        } catch (e: Exception) {
            throw AssertionError("Test '$testName' failed with configuration '$description': ${e.message}", e)
        }
    }
}
