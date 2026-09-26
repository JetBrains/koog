package ai.koog.prompt.processor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.jvm.JvmOverloads

/**
 * A response processor that coerces stringified structured tool call arguments into their declared JSON shapes.
 *
 * LLMs sometimes emit an argument whose schema declares an object or an array as a JSON-encoded string instead,
 * e.g. `{"target": "{\"type\":\"path\"}"}` rather than `{"target": {"type": "path"}}`. Tools that validate their
 * input strictly (for example, tools served by MCP servers) reject such calls.
 *
 * This processor inspects [MessagePart.Tool.Call] parts of an LLM response before tool execution and, guided by
 * the [ToolDescriptor]s available for the current LLM call, parses such stringified values back into the shape
 * declared by the matching tool parameter:
 *
 * - [ToolParameterType.Object]: a string value is parsed only if it yields a JSON object. Declared properties are
 *   coerced recursively; keys not declared in the descriptor are coerced only when the descriptor allows
 *   additional properties and declares their type, and are preserved otherwise.
 * - [ToolParameterType.List]: a string value is parsed only if it yields a JSON array. Items are coerced recursively.
 * - [ToolParameterType.AnyOf]: the first candidate whose shape is compatible with the actual (or parsed) value is
 *   used. If the original string value is itself a valid candidate, it is preserved.
 * - Primitive-like types ([ToolParameterType.String], [ToolParameterType.Integer], [ToolParameterType.Float],
 *   [ToolParameterType.Boolean], [ToolParameterType.Enum], [ToolParameterType.Null]) are never modified.
 *
 * The processor is fail-soft by design: unknown tools, unknown argument keys, malformed tool call arguments,
 * parse failures, and shape mismatches all leave the original value untouched. Recursion is bounded; values
 * nested deeper than 8 levels are preserved as-is. Final argument validation remains the responsibility of
 * whatever executes the tool call, so this processor normalizes shapes as a post-processing step instead of
 * silently rewriting arguments at tool execution time.
 *
 * Typical usage chains it after [ManualToolCallFixProcessor], which normalizes malformed responses into
 * [MessagePart.Tool.Call] parts first:
 *
 * ```kotlin
 * val processor = ManualToolCallFixProcessor(toolRegistry) + ToolCallArgumentCoercionProcessor()
 * ```
 *
 * @param json The json parser used to parse stringified argument values
 */
public class ToolCallArgumentCoercionProcessor @JvmOverloads constructor(
    private val json: Json = ToolCallJsonConfig.defaultJson,
) : ResponseProcessor() {

    private companion object {
        private const val MAX_DEPTH = 8
    }

    override suspend fun process(
        executor: PromptExecutor,
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
        response: Message.Assistant,
        serializer: JSONSerializer,
    ): Message.Assistant {
        val descriptorsByName = tools.associateBy { it.name }
        return Message.Assistant(
            parts = response.parts.map { part ->
                when (part) {
                    is MessagePart.Tool.Call -> coerceToolCall(part, descriptorsByName[part.tool])
                    else -> part
                }
            },
            finishReason = response.finishReason,
            metaInfo = response.metaInfo
        )
    }

    private fun coerceToolCall(part: MessagePart.Tool.Call, descriptor: ToolDescriptor?): MessagePart.Tool.Call {
        if (descriptor == null) return part

        val args = runCatching { part.argsJson }.getOrNull() ?: return part
        val paramsByName = (descriptor.requiredParameters + descriptor.optionalParameters).associateBy { it.name }

        val coercedArgs = buildJsonObject {
            for ((key, value) in args) {
                val parameter = paramsByName[key]
                put(key, if (parameter != null) coerceValue(value, parameter.type, depth = 1) else value)
            }
        }

        return if (coercedArgs == args) {
            part
        } else {
            MessagePart.Tool.Call(id = part.id, tool = part.tool, args = coercedArgs)
        }
    }

    private fun coerceValue(value: JsonElement, type: ToolParameterType, depth: Int): JsonElement {
        if (depth > MAX_DEPTH) return value
        return when (type) {
            is ToolParameterType.Object -> coerceObject(value, type, depth)
            is ToolParameterType.List -> coerceList(value, type, depth)
            is ToolParameterType.AnyOf -> coerceAnyOf(value, type, depth)
            else -> value
        }
    }

    private fun coerceObject(value: JsonElement, type: ToolParameterType.Object, depth: Int): JsonElement {
        val obj = value as? JsonObject
            ?: parseStringValueOrNull(value) as? JsonObject
            ?: return value

        val propertiesByName = type.properties.associateBy { it.name }
        val additionalPropertiesType = type.additionalPropertiesType.takeIf { type.additionalProperties == true }

        return buildJsonObject {
            for ((key, child) in obj) {
                val childType = propertiesByName[key]?.type ?: additionalPropertiesType
                put(key, if (childType != null) coerceValue(child, childType, depth + 1) else child)
            }
        }
    }

    private fun coerceList(value: JsonElement, type: ToolParameterType.List, depth: Int): JsonElement {
        val array = value as? JsonArray
            ?: parseStringValueOrNull(value) as? JsonArray
            ?: return value

        return JsonArray(array.map { coerceValue(it, type.itemsType, depth + 1) })
    }

    private fun coerceAnyOf(value: JsonElement, type: ToolParameterType.AnyOf, depth: Int): JsonElement {
        val directCandidate = type.types.firstOrNull { isShapeCompatible(value, it.type) }
        if (directCandidate != null) return coerceValue(value, directCandidate.type, depth)

        val parsed = parseStringValueOrNull(value) ?: return value
        val parsedCandidate = type.types.firstOrNull { isShapeCompatible(parsed, it.type) } ?: return value
        return coerceValue(parsed, parsedCandidate.type, depth)
    }

    /**
     * Parses a string-primitive [value] into a structured json element.
     * Returns null for non-string values, parse failures, and values that do not parse into an object or an array,
     * so callers fall back to the original value.
     */
    private fun parseStringValueOrNull(value: JsonElement): JsonElement? {
        if (value !is JsonPrimitive || !value.isString) return null
        val parsed = runCatching { json.parseToJsonElement(value.content) }.getOrNull() ?: return null
        return parsed.takeIf { it is JsonObject || it is JsonArray }
    }

    private fun isShapeCompatible(value: JsonElement, type: ToolParameterType): Boolean = when (type) {
        is ToolParameterType.Object -> value is JsonObject
        is ToolParameterType.List -> value is JsonArray
        is ToolParameterType.AnyOf -> type.types.any { isShapeCompatible(value, it.type) }
        is ToolParameterType.Enum -> value is JsonPrimitive && value.isString
        ToolParameterType.String -> value is JsonPrimitive && value.isString
        ToolParameterType.Null -> value is JsonNull
        ToolParameterType.Boolean -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
        ToolParameterType.Integer -> value is JsonPrimitive && !value.isString && value.longOrNull != null
        ToolParameterType.Float -> value is JsonPrimitive && !value.isString && value.doubleOrNull != null
    }
}
