package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Maximum recursion depth applied when walking nested descriptor structures.
 *
 * Any input that would coerce deeper than this guard is left untouched so that adversarial or
 * accidentally cyclic payloads cannot exhaust the stack.
 */
private const val MAX_COERCION_DEPTH: Int = 8

/**
 * Coerces stringified JSON arguments emitted by an LLM into the shapes declared by the tool's
 * [ToolDescriptor] before they are forwarded to the MCP server.
 *
 * ### Gating
 * Coercion is intentionally restricted to declared parameter types of
 * [ToolParameterType.Object], [ToolParameterType.AnyOf] and [ToolParameterType.List].
 * Primitive parameter types ([ToolParameterType.String], [ToolParameterType.Integer],
 * [ToolParameterType.Float], [ToolParameterType.Boolean], [ToolParameterType.Enum],
 * [ToolParameterType.Null]) are never touched: forwarding them unchanged preserves user-intended
 * strings (e.g. `"{not-json}"`) and lets the MCP server's primitive validation remain authoritative.
 *
 * ### Behaviour
 * For each argument whose declared parameter type is one of the gated types and whose value is a
 * [JsonPrimitive] with `isString == true`, [Json.parseToJsonElement] is invoked on the string
 * content. The parsed shape is then checked against the declared type:
 *  - [ToolParameterType.Object] accepts only a parsed [JsonObject].
 *  - [ToolParameterType.List] accepts only a parsed [JsonArray].
 *  - [ToolParameterType.AnyOf] accepts the parsed value when the **first** candidate whose shape
 *    is compatible with the parsed element is found. If no candidate matches, the value is left
 *    untouched.
 *
 * On parse failure or shape mismatch the **original value is preserved** so the MCP server's
 * schema validation remains the source of truth. The function never throws for malformed input.
 *
 * ### Recursion
 * Object properties, list items and AnyOf candidates are walked recursively so that deeply nested
 * stringified payloads (e.g. a stringified object containing another stringified object) are
 * unwrapped consistently. Recursion is bounded by [MAX_COERCION_DEPTH]; values that would be
 * coerced past the guard are preserved unchanged. AnyOf direct-match recursion shares the same
 * depth budget as object/list recursion, so the guard fires uniformly regardless of how a value
 * was routed.
 *
 * ### Additional properties
 * For [ToolParameterType.Object] parameters, declared `properties` are matched first. Keys that
 * are not declared are coerced using [ToolParameterType.Object.additionalPropertiesType] only when
 * [ToolParameterType.Object.additionalProperties] is `true` **and** the additional type is
 * non-null. Otherwise unmatched keys are passed through unchanged.
 *
 * @param args The raw arguments produced by the agent framework.
 * @param descriptor The descriptor of the MCP tool whose parameter types guide coercion.
 * @return A [JsonObject] whose values have been coerced where applicable; entries without a
 *         matching parameter descriptor are preserved as-is.
 */
internal fun coerceArgsToDescriptorTypes(
    args: JsonObject,
    descriptor: ToolDescriptor,
): JsonObject {
    val paramsByName: Map<String, ToolParameterDescriptor> =
        (descriptor.requiredParameters + descriptor.optionalParameters).associateBy { it.name }

    return buildJsonObject {
        for ((key, value) in args) {
            val param = paramsByName[key]
            if (param == null) {
                put(key, value)
            } else {
                put(key, coerceValue(value, param.type, depth = 1))
            }
        }
    }
}

/**
 * Recursively coerces [value] against the declared [type], obeying the gating, shape and depth
 * rules described on [coerceArgsToDescriptorTypes].
 */
private fun coerceValue(value: JsonElement, type: ToolParameterType, depth: Int): JsonElement {
    if (depth > MAX_COERCION_DEPTH) return value

    return when (type) {
        is ToolParameterType.Object -> coerceObject(value, type, depth)
        is ToolParameterType.List -> coerceList(value, type, depth)
        is ToolParameterType.AnyOf -> coerceAnyOf(value, type, depth)
        else -> value
    }
}

private fun coerceObject(value: JsonElement, type: ToolParameterType.Object, depth: Int): JsonElement {
    val asObject: JsonObject? = when {
        value is JsonObject -> value
        value is JsonPrimitive && value.isString ->
            tryParseAs<JsonObject>(value.content)
        else -> null
    }
    if (asObject == null) return value

    val declaredByName: Map<String, ToolParameterDescriptor> = type.properties.associateBy { it.name }
    val additionalAllowed = type.additionalProperties == true && type.additionalPropertiesType != null

    return buildJsonObject {
        for ((k, v) in asObject) {
            val declared = declaredByName[k]
            val coerced = when {
                declared != null -> coerceValue(v, declared.type, depth + 1)
                additionalAllowed -> coerceValue(v, type.additionalPropertiesType!!, depth + 1)
                else -> v
            }
            put(k, coerced)
        }
    }
}

private fun coerceList(value: JsonElement, type: ToolParameterType.List, depth: Int): JsonElement {
    val asArray: JsonArray? = when {
        value is JsonArray -> value
        value is JsonPrimitive && value.isString ->
            tryParseAs<JsonArray>(value.content)
        else -> null
    }
    if (asArray == null) return value

    return buildJsonArray {
        for (element in asArray) {
            add(coerceValue(element, type.itemsType, depth + 1))
        }
    }
}

private fun coerceAnyOf(value: JsonElement, type: ToolParameterType.AnyOf, depth: Int): JsonElement {
    // If the current value already matches some candidate shape, recurse into that candidate so
    // nested stringified payloads keep being unwrapped.
    val directMatch = type.types.firstOrNull { isShapeCompatible(value, it.type) }
    if (directMatch != null) {
        return coerceValue(value, directMatch.type, depth + 1)
    }

    if (value is JsonPrimitive && value.isString) {
        val parsed = tryParse(value.content) ?: return value
        val candidate = type.types.firstOrNull { isShapeCompatible(parsed, it.type) } ?: return value
        return coerceValue(parsed, candidate.type, depth + 1)
    }

    return value
}

private fun isShapeCompatible(value: JsonElement, type: ToolParameterType): Boolean = when (type) {
    is ToolParameterType.Object -> value is JsonObject
    is ToolParameterType.List -> value is JsonArray
    is ToolParameterType.String -> value is JsonPrimitive && value.isString
    is ToolParameterType.Integer, is ToolParameterType.Float ->
        value is JsonPrimitive && !value.isString && value.content.toDoubleOrNull() != null
    is ToolParameterType.Boolean ->
        value is JsonPrimitive &&
            !value.isString &&
            (value.content == "true" || value.content == "false")
    is ToolParameterType.Null -> value is JsonPrimitive && !value.isString && value.content == "null"
    is ToolParameterType.Enum ->
        value is JsonPrimitive &&
            value.isString &&
            type.entries.contains(value.content)
    is ToolParameterType.AnyOf -> type.types.any { isShapeCompatible(value, it.type) }
}

private inline fun <reified T : JsonElement> tryParseAs(content: String): T? {
    val parsed = tryParse(content) ?: return null
    return parsed as? T
}

private fun tryParse(content: String): JsonElement? = try {
    Json.parseToJsonElement(content)
} catch (_: Throwable) {
    null
}
