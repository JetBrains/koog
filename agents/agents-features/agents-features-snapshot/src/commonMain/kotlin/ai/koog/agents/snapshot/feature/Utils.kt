@file:Suppress("MissingKDocForPublicAPI")

package ai.koog.agents.snapshot.feature

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

public inline fun <reified T> serializeInput(input: T?): String? {
    if (input == null) return null
    if (input is String) return input

    return try {
        Json.encodeToString(input)
    } catch (e: SerializationException) {
        throw IllegalArgumentException("Cannot serialize input of type ${input::class.simpleName}", e)
    }
}

internal inline fun <reified T> deserializeInput(input: String?): T? {
    if (input == null) return null
    return try {
        Json.decodeFromString(input)
    } catch (e: SerializationException) {
        throw IllegalArgumentException("Cannot deserialize input of type ${T::class.simpleName}", e)
    }
}
