package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.utils.HiddenString
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents an abstract field to be included in an event's body. Each field is characterized
 * by a unique key and an associated value, and optionally marked as verbose for additional significance.
 */
public abstract class EventBodyField {

    private companion object {
        private val logger = KotlinLogging.logger { }
    }

    /**
     * Field key.
     */
    public abstract val key: String

    /**
     * Field value.
     */
    public abstract val value: Any

    /**
     * Serializes [value] to a JSON string.
     *
     * @param verbose When `true`, [HiddenString] payloads are emitted unmasked.
     */
    public fun valueString(verbose: Boolean): String {
        return Json.encodeToString(JsonElement.serializer(), convertValueToJsonElement(value, verbose))
    }

    /**
     * Converts [value] to a [JsonElement].
     *
     * @param value Value to convert.
     * @param verbose When `true`, [HiddenString] payloads are emitted unmasked.
     */
    public fun convertValueToJsonElement(value: Any, verbose: Boolean): JsonElement {
        return when (value) {
            is HiddenString -> {
                JsonPrimitive(
                    if (verbose) {
                        value.value
                    } else {
                        value.toString()
                    }
                )
            }
            is CharSequence -> JsonPrimitive(value.toString())
            is Char -> JsonPrimitive(value.toString())
            is Boolean -> JsonPrimitive(value)
            is Int, is Long,
            is Double, is Float -> {
                JsonPrimitive(value)
            }
            is List<*> -> JsonArray(
                value
                    .filterNotNull()
                    .map { convertValueToJsonElement(it, verbose) }
            )
            is Map<*, *> -> JsonObject(
                value.entries
                    .filter { it.key != null }
                    .associate { (k, v) ->
                        k.toString() to (v?.let { convertValueToJsonElement(it, verbose) } ?: JsonNull)
                    }
            )
            else -> {
                logger.debug { "$key: Custom type for event body: ${value::class.simpleName}. Use toString()" }
                JsonPrimitive(value.toString())
            }
        }
    }
}
