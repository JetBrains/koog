package ai.koog.prompt.executor.clients.litert

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.google.ai.edge.litertlm.Message as LitertMessage

/**
 * Converts a LiteRT [LitertMessage] to a koog [Message.Assistant].
 *
 * Text content parts are mapped to [MessagePart.Text] parts and tool calls
 * are each mapped to a [MessagePart.Tool.Call] part within the same assistant
 * message. Non-text content parts are not supported and will throw
 * [UnsupportedOperationException].
 *
 * LiteRT [ToolCall] does not expose a stable call id, but Koog requires one to
 * correlate [MessagePart.Tool.Result] with [MessagePart.Tool.Call]. A synthetic,
 * unique id is therefore generated per tool call (see [syntheticToolCallId]);
 * it is used only inside Koog and is dropped when converting back to LiteRT,
 * where tool responses are correlated by tool name.
 *
 * @param clock Clock used to populate [ResponseMetaInfo] timestamps.
 * @return An assistant message with text and/or tool-call parts derived from the response.
 */
internal fun LitertMessage.toKoogMessage(clock: KoogClock): Message.Assistant {
    val parts = buildList {
        contents.contents.forEach {
            when (it) {
                is Content.Text -> add(MessagePart.Text(it.text))
                else -> throw UnsupportedOperationException(
                    "Unsupported LiteRT content type: ${it::class.simpleName}"
                )
            }
        }
        toolCalls.forEachIndexed { index, toolCall ->
            add(
                MessagePart.Tool.Call(
                    id = syntheticToolCallId(toolCall.name, index),
                    tool = toolCall.name,
                    args = toolCall.arguments.toJsonObject()
                )
            )
        }
    }
    return Message.Assistant(
        parts = parts,
        metaInfo = ResponseMetaInfo.create(clock),
    )
}

/**
 * Builds a unique synthetic id for a LiteRT [ToolCall]. LiteRT does not expose
 * call ids, so Koog synthesizes one to satisfy
 * [MessagePart.Tool.Call]/[MessagePart.Tool.Result] correlation requirements.
 *
 * The id includes the tool name and its position inside the assistant message
 * for readability/debuggability, plus a random UUID suffix to guarantee
 * uniqueness across messages (otherwise, repeated calls to the same tool would
 * collide on ids like `litert-<name>-0`). The id is not transmitted back to
 * LiteRT — tool responses are correlated by tool name on the next turn.
 */
@OptIn(ExperimentalUuidApi::class)
private fun syntheticToolCallId(name: String, index: Int): String =
    "litert-$name-$index-${Uuid.random()}"

/**
 * Converts a LiteRT tool-call arguments map (`Map<String, Any?>` of primitives,
 * collections and maps) into a kotlinx.serialization [JsonObject], without
 * relying on the MCP SDK's `toJson` extension. Unknown reference types fall
 * back to their `toString()` representation as a JSON string.
 */
private fun Map<String, Any?>.toJsonObject(): JsonObject =
    JsonObject(mapValues { (_, v) -> v.toJsonElement() })

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    else -> JsonPrimitive(toString())
}

/**
 * Adds a `"description"` field to the surrounding JSON object only when [description]
 * is non-null and non-blank. Emitting `"description": null` (or empty strings) is
 * known to confuse LiteRT/Gemma function-calling schema parsers.
 */
private fun JsonObjectBuilder.putDescriptionIfPresent(description: String?) {
    if (!description.isNullOrBlank()) put("description", description)
}

/**
 * Converts a koog [Message] to a LiteRT [LitertMessage].
 *
 * Dispatch is by message subtype, then by parts so tool-call round trips are preserved:
 * - [Message.System] → `LitertMessage.system`
 * - [Message.User] with a [MessagePart.Tool.Result] part → `LitertMessage.tool` with [Content.ToolResponse] entries;
 *   any [MessagePart.Text] parts in the same message are included as [Content.Text] entries in the same tool message
 * - [Message.User] otherwise → `LitertMessage.user`
 * - [Message.Assistant] with [MessagePart.Tool.Call] parts → `LitertMessage.model` carrying the calls in `toolCalls`
 * - [Message.Assistant] otherwise → `LitertMessage.model`
 *
 * [MessagePart.Reasoning] parts are not yet supported and throw [UnsupportedOperationException].
 */
internal fun Message.toLitertMessage(): LitertMessage {
    return when (this) {
        is Message.System -> LitertMessage.system(parts.joinToString("\n") { it.text })
        is Message.User -> {
            val toolResults = parts.filterIsInstance<MessagePart.Tool.Result>()
            if (toolResults.isNotEmpty()) {
                val responseContents: List<Content> = parts.mapNotNull { part ->
                    when (part) {
                        is MessagePart.Tool.Result ->
                            Content.ToolResponse(name = part.tool, response = parseToolResponse(part.output))
                        is MessagePart.Text ->
                            Content.Text(part.text)
                        else -> null
                    }
                }
                LitertMessage.tool(Contents.of(responseContents))
            } else {
                val text = parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
                LitertMessage.user(text)
            }
        }
        is Message.Assistant -> {
            if (parts.any { it is MessagePart.Reasoning }) {
                throw UnsupportedOperationException("Reasoning is not yet supported")
            }
            val toolCalls = parts.filterIsInstance<MessagePart.Tool.Call>()
            val text = parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
            if (toolCalls.isNotEmpty()) {
                // LiteRT ToolCall has no id field, so the koog-internal synthetic id
                // is intentionally dropped here. Tool responses will be correlated
                // back by tool name on the next turn.
                val textContents: List<Content> =
                    if (text.isNotEmpty()) listOf(Content.Text(text)) else emptyList()
                LitertMessage.model(
                    contents = Contents.of(textContents),
                    toolCalls = toolCalls.map {
                        ToolCall(name = it.tool, arguments = parseToolArguments(it.args))
                    },
                )
            } else {
                LitertMessage.model(text)
            }
        }
    }
}

/**
 * Parses a tool-call arguments JSON string into a `Map<String, Any?>` for LiteRT's [ToolCall].
 *
 * Returns an empty map when [content] is blank. Throws [IllegalArgumentException] if
 * the content is not a valid JSON object; tool-arguments parse failures are a common
 * symptom of model misbehavior and should not be silently turned into empty arguments.
 */
private fun parseToolArguments(content: String): Map<String, Any?> {
    if (content.isBlank()) return emptyMap()
    val element: JsonElement = try {
        Json.parseToJsonElement(content)
    } catch (e: SerializationException) {
        throw IllegalArgumentException("Tool call arguments are not valid JSON: $content", e)
    }
    val obj = element as? JsonObject
        ?: throw IllegalArgumentException("Tool call arguments must be a JSON object, got: $element")
    return obj.mapValues { (_, v) -> v.toAnyOrNull() }
}

/**
 * Parses a tool-result JSON string into a structured value (Map/List/primitive) for
 * LiteRT's [Content.ToolResponse]. Falls back to the raw string when the content is
 * not valid JSON, which is acceptable because [Content.ToolResponse.response] is `Any?`.
 */
private fun parseToolResponse(content: String): Any? {
    if (content.isBlank()) return content
    return runCatching { Json.parseToJsonElement(content).toAnyOrNull() }.getOrDefault(content)
}

/**
 * Converts a [JsonElement] tree into plain Kotlin values:
 * `JsonNull` → `null`, primitives → `String`/`Long`/`Double`/`Boolean`,
 * `JsonArray` → `List<Any?>`, `JsonObject` → `Map<String, Any?>`.
 */
private fun JsonElement.toAnyOrNull(): Any? {
    return when (this) {
        is JsonNull -> null
        is JsonPrimitive -> {
            when {
                isString -> content
                content.equals("true", ignoreCase = false) -> true
                content.equals("false", ignoreCase = false) -> false
                // JSON numeric literals containing a fractional or exponent component
                // must round-trip as Double, not silently truncate to Long.
                '.' in content || 'e' in content || 'E' in content ->
                    content.toDoubleOrNull() ?: content
                else ->
                    content.toLongOrNull()
                        ?: content.toDoubleOrNull()
                        ?: content.toBooleanStrictOrNull()
                        ?: content
            }
        }
        is JsonArray -> map { it.toAnyOrNull() }
        is JsonObject -> mapValues { (_, v) -> v.toAnyOrNull() }
    }
}

/**
 * Converts a [ToolParameterType] to its OpenAPI-compatible JSON schema [JsonObject].
 *
 * Handles all parameter types recursively:
 * - Primitives (`String`, `Integer`, `Float`, `Boolean`, `Null`) emit a `"type"` field.
 * - [ToolParameterType.Enum] emits `"type": "string"` plus an `"enum"` array.
 * - [ToolParameterType.List] emits `"type": "array"` with a recursive `"items"` schema.
 * - [ToolParameterType.Object] emits `"type": "object"` with `"properties"` and `"required"`.
 * - [ToolParameterType.AnyOf] emits an `"anyOf"` array of type schemas.
 *
 * Descriptions are NOT included here; callers should add `"description"` to the
 * enclosing property object.
 */
private fun ToolParameterType.toJsonSchema(): JsonObject = buildJsonObject {
    when (val type = this@toJsonSchema) {
        ToolParameterType.String -> put("type", "string")
        ToolParameterType.Integer -> put("type", "integer")
        ToolParameterType.Float -> put("type", "number")
        ToolParameterType.Boolean -> put("type", "boolean")
        ToolParameterType.Null -> put("type", "null")
        is ToolParameterType.Enum -> {
            put("type", "string")
            put("enum", JsonArray(type.entries.map { JsonPrimitive(it) }))
        }
        is ToolParameterType.List -> {
            put("type", "array")
            put("items", type.itemsType.toJsonSchema())
        }
        is ToolParameterType.Object -> {
            put("type", "object")
            put(
                "properties",
                buildJsonObject {
                    for (prop in type.properties) {
                        put(
                            prop.name,
                            buildJsonObject {
                                prop.type.toJsonSchema().forEach { (k, v) -> put(k, v) }
                                putDescriptionIfPresent(prop.description)
                            }
                        )
                    }
                }
            )
            if (type.requiredProperties.isNotEmpty()) {
                put("required", JsonArray(type.requiredProperties.map { JsonPrimitive(it) }))
            }
        }
        is ToolParameterType.AnyOf -> {
            put(
                "anyOf",
                JsonArray(
                    type.types.map { descriptor ->
                        buildJsonObject {
                            descriptor.type.toJsonSchema().forEach { (k, v) -> put(k, v) }
                            putDescriptionIfPresent(descriptor.description)
                        }
                    }
                )
            )
        }
    }
}

/**
 * Adapts a koog [ToolDescriptor] to the LiteRT [OpenApiTool] interface.
 *
 * This adapter is used to register koog tools with a LiteRT [Conversation] so the
 * model is aware of available tools during inference. Tool execution is handled by
 * the koog agent framework, not by LiteRT, so [execute] always throws.
 *
 * @property tool The koog tool descriptor to expose to LiteRT.
 */
internal class AndroidLocalTool(val tool: ToolDescriptor) : OpenApiTool {
    /**
     * Returns the tool schema as an OpenAPI-compatible JSON string.
     *
     * The schema includes [ToolDescriptor.name], [ToolDescriptor.description], and a
     * `parameters` object with all required and optional parameters as JSON Schema
     * property entries. Only [ToolDescriptor.requiredParameters] are listed in `required`.
     */
    override fun getToolDescriptionJsonString(): String {
        val allParams = tool.requiredParameters + tool.optionalParameters
        return buildJsonObject {
            put("name", tool.name)
            putDescriptionIfPresent(tool.description)
            put(
                "parameters",
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            for (param in allParams) {
                                put(
                                    param.name,
                                    buildJsonObject {
                                        param.type.toJsonSchema().forEach { (k, v) -> put(k, v) }
                                        putDescriptionIfPresent(param.description)
                                    }
                                )
                            }
                        }
                    )
                    if (tool.requiredParameters.isNotEmpty()) {
                        put("required", JsonArray(tool.requiredParameters.map { JsonPrimitive(it.name) }))
                    }
                }
            )
        }.toString()
    }

    /**
     * Not supported — tool execution is performed by the koog agent framework.
     *
     * @throws UnsupportedOperationException always.
     */
    override fun execute(paramsJsonString: String): String {
        throw UnsupportedOperationException("Should not be called")
    }
}
