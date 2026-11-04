package ai.koog.a2a.transport.jsonrpc.serialization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Rewrites snake_case keys of a JSON tree to the lowerCamelCase names the A2A models are decoded with.
 *
 * The A2A JSON-RPC binding uses camelCase ProtoJSON names, but ProtoJSON parsers also accept the original proto
 * field names, and some peers (e.g. the A2A TCK) send those. [JSONRPCJson] ignores unknown keys, so they would be
 * dropped silently. This is a transport quirk, so it is applied to JSON-RPC `params` and `result` only, and the
 * models stay unaware of it.
 *
 * A key containing `_` is converted with the protobuf lowerCamel rule: each `_` is dropped and the character after
 * it is uppercased (`history_length` -> `historyLength`). Other keys and all values are kept as is. Keys are then
 * matched, by their converted name, against:
 * - [opaqueKeys]: free-form fields, whose keys are user data, so their value is kept untouched;
 * - [userKeyedMaps]: maps keyed by user-chosen names, whose values are A2A objects: the keys are kept and only the
 *   values are normalized.
 */
internal object SnakeCaseKeyNormalizer {
    /**
     * `metadata` (messages, tasks, parts, artifacts, events, requests), `data` (DataPart), `params` (AgentExtension),
     * `header` (AgentCardSignature), `schemes` (SecurityRequirement), `scopes` (OAuth flows).
     */
    val opaqueKeys: Set<String> = setOf("metadata", "data", "params", "header", "schemes", "scopes")

    /**
     * Maps keyed by user-chosen names whose values are A2A objects: keys are kept, values normalized.
     * E.g. `securitySchemes` (AgentCard), keyed by scheme name.
     */
    val userKeyedMaps: Set<String> = setOf("securitySchemes")

    /**
     * Returns [element] with its keys normalized.
     *
     * @throws SerializationException if an object has the same key in more than one spelling, e.g. both
     * `historyLength` and `history_length`, as it is ambiguous which value to use.
     */
    fun normalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> normalizeObject(element)
        is JsonArray -> JsonArray(element.map(::normalize))
        is JsonPrimitive -> element
    }

    private fun normalizeObject(obj: JsonObject): JsonObject {
        val result = LinkedHashMap<String, JsonElement>(obj.size)

        for ((key, value) in obj) {
            val name = toLowerCamel(key)
            if (name in result) {
                throw SerializationException("Duplicate key '$name': sent in more than one spelling")
            }

            result[name] = when (name) {
                in opaqueKeys -> value
                in userKeyedMaps if value is JsonObject -> JsonObject(value.mapValues { normalize(it.value) })
                else -> normalize(value)
            }
        }

        return JsonObject(result)
    }

    private fun toLowerCamel(key: String): String {
        if ('_' !in key) return key

        return buildString(key.length) {
            var upperNext = false
            for (c in key) {
                when {
                    c == '_' -> upperNext = true

                    upperNext -> {
                        append(c.uppercaseChar())
                        upperNext = false
                    }

                    else -> append(c)
                }
            }
        }
    }
}
