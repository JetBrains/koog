package ai.koog.prompt.executor.clients.google.structure

import kotlinx.serialization.json.JsonObject

/**
 * Helper class to work with different response schema types in a more convenient way.
 * Google API is flat in this regard instead of being polymorphic, which is inconvenient to work with in client.
 */
internal data class GoogleResponseFormat(
    val responseMimeType: String,
    val responseSchema: JsonObject? = null,
    val responseJsonSchema: JsonObject? = null
)
