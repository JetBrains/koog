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
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.google.ai.edge.litertlm.Message as LitertMessage

/**
 * Converts a LiteRT [LitertMessage] to a koog [Message.Assistant].
 *
 * Text content parts are mapped to [MessagePart.Text] parts and tool calls
 * are each mapped to a [MessagePart.Tool.Call] part within the same assistant
 * message. Non-text content parts are not supported and will throw
 * [UnsupportedOperationException].
 *
 * LiteRT [ToolCall] does not expose a stable call id. Koog uses tool-call ids to
 * correlate [MessagePart.Tool.Result] with [MessagePart.Tool.Call]. Therefore this
 * client currently supports only a single tool call per LiteRT response. Multiple
 * tool calls must either be rejected or handled by synthesizing stable ids and
 * batching tool responses back to LiteRT in order. Until proper support exists,
 * this function fails fast with [UnsupportedOperationException] when more than
 * one tool call is present.
 *
 * @param clock Clock used to populate [ResponseMetaInfo] timestamps.
 * @return An assistant message with text and/or tool-call parts derived from the response.
 */
internal fun LitertMessage.toKoogMessage(clock: KoogClock): Message.Assistant {
    if (toolCalls.size > 1) {
        throw UnsupportedOperationException(
            "LiteRT client does not currently support multiple tool calls in one response because LiteRT ToolCall has no id for result correlation"
        )
    }
    val parts = buildList<MessagePart.ResponsePart> {
        contents.contents.forEach {
            when (it) {
                is Content.Text -> add(MessagePart.Text(it.text))
                else -> throw UnsupportedOperationException("Only text message responses are supported")
            }
        }
        toolCalls.forEach { toolCall ->
            add(
                MessagePart.Tool.Call(
                    id = null,
                    tool = toolCall.name,
                    args = JsonObject(toolCall.arguments.toJson()).toString(),
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
 * Converts a koog [Message] to a LiteRT [LitertMessage].
 *
 * Dispatch is by message subtype, then by parts so tool-call round trips are preserved:
 * - [Message.System] → `LitertMessage.system`
 * - [Message.User] with a [MessagePart.Tool.Result] part → `LitertMessage.tool` with a [Content.ToolResponse]
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
            val toolResult = parts.filterIsInstance<MessagePart.Tool.Result>().firstOrNull()
            if (toolResult != null) {
                val parsedResponse: Any? = parseToolResponse(toolResult.output)
                LitertMessage.tool(Contents.of(Content.ToolResponse(name = toolResult.tool, response = parsedResponse)))
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
            if (toolCalls.isNotEmpty()) {
                LitertMessage.model(
                    contents = Contents.of(emptyList()),
                    toolCalls = toolCalls.map { ToolCall(name = it.tool, arguments = parseToolArguments(it.args)) },
                )
            } else {
                val text = parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }
                LitertMessage.model(text)
            }
        }
    }
}

/**
 * Parses a tool-call arguments JSON string into a `Map<String, Any?>` for LiteRT's [ToolCall].
 *
 * Returns an empty map if [content] is blank or cannot be parsed as a JSON object.
 */
private fun parseToolArguments(content: String): Map<String, Any?> {
    if (content.isBlank()) return emptyMap()
    return runCatching {
        Json.parseToJsonElement(content).jsonObject.mapValues { (_, v) -> v.toAnyOrNull() }
    }.getOrElse { emptyMap() }
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
        is kotlinx.serialization.json.JsonNull -> null
        is JsonPrimitive -> {
            if (isString) {
                content
            } else {
                content.toLongOrNull()
                    ?: content.toDoubleOrNull()
                    ?: content.toBooleanStrictOrNull()
                    ?: content
            }
        }
        is kotlinx.serialization.json.JsonArray -> map { it.toAnyOrNull() }
        is kotlinx.serialization.json.JsonObject -> mapValues { (_, v) -> v.toAnyOrNull() }
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
                                put("description", prop.description)
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
            put("description", tool.description)
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
                                        put("description", param.description)
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
