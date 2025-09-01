package ai.koog.test.utils

import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies that a given [payload] can be deserialized to a given [T] type and then re-serialized to the same payload.
 */
public inline fun <reified T : Any> verifyDeserialization(payload: String, json: Json = Json): T {
    val payloadJsonElement = runCatching { Json.parseToJsonElement(payload) }.getOrNull()

    requireNotNull(payloadJsonElement) { "Payload should be valid JSON: ```\n$payload\n```" }

    val model: T = json.decodeFromString(payload)

    assertNotNull(model) {
        "JSON Payload could not be deserialized to ${T::class.simpleName}: ```\n$payload\n```"
    }

    val encoded = json.encodeToString(model)

    assertNotNull(encoded) {
        val jsonElement = Json.parseToJsonElement(encoded)
        assertEquals(
            expected = payloadJsonElement,
            actual = jsonElement,
            message = "Deserialized model should generate original payload"
        )
    }

    return model
}
