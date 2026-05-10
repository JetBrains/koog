package com.jetbrains.example.koog.compose.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.OpenApiTool
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlin.time.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.google.ai.edge.litertlm.Message as LitertMessage

/**
 * Converts a LiteRT [LitertMessage] to a list of koog [Message.Response] objects.
 *
 * Text content parts are mapped to [Message.Assistant] messages and tool calls
 * are each mapped to a separate [Message.Tool.Call]. Non-text content parts are
 * not supported and will throw [UnsupportedOperationException].
 *
 * @param clock Clock used to populate [ResponseMetaInfo] timestamps.
 * @return List containing assistant and/or tool-call messages derived from the response.
 */
internal fun LitertMessage.toKoogMessages(clock: Clock): List<Message.Response> {
    return buildList {
        if (contents.contents.isNotEmpty()) {
            val parts = contents.contents.map {
                when (it) {
                    is Content.Text -> ContentPart.Text(it.text)
                    else -> throw UnsupportedOperationException("Only text message responses are supported")
                }
            }
            add(
                Message.Assistant(
                    parts = parts,
                    metaInfo = ResponseMetaInfo.create(clock),
                )
            )
        }

        if (toolCalls.isNotEmpty()) {
            toolCalls.forEach { toolCall ->
                add(
                    Message.Tool.Call(
                        id = null,
                        tool = toolCall.name,
                        content = toolCall.arguments.toJson().toString(),
                        metaInfo = ResponseMetaInfo.create(clock),
                    )
                )
            }
        }
    }
}

/**
 * Converts a koog [Message] to a LiteRT [LitertMessage].
 *
 * Maps each [Message.Role] to the corresponding LiteRT factory:
 * - [Message.Role.System] → `LitertMessage.system`
 * - [Message.Role.User] → `LitertMessage.user`
 * - [Message.Role.Assistant] → `LitertMessage.model`
 * - [Message.Role.Tool] → `LitertMessage.tool`
 *
 * [Message.Role.Reasoning] is not yet supported and throws [UnsupportedOperationException].
 */
internal fun Message.toLitertMessage(): LitertMessage {
    return when (role) {
        Message.Role.System -> LitertMessage.system(content)
        Message.Role.User -> LitertMessage.user(content)
        Message.Role.Assistant -> LitertMessage.model(content)
        Message.Role.Tool -> LitertMessage.tool(Contents.of(content))
        Message.Role.Reasoning -> throw UnsupportedOperationException("Reasoning is not yet supported")
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
            put("properties", buildJsonObject {
                for (prop in type.properties) {
                    put(prop.name, buildJsonObject {
                        prop.type.toJsonSchema().forEach { (k, v) -> put(k, v) }
                        put("description", prop.description)
                    })
                }
            })
            if (type.requiredProperties.isNotEmpty()) {
                put("required", JsonArray(type.requiredProperties.map { JsonPrimitive(it) }))
            }
        }
        is ToolParameterType.AnyOf -> {
            put("anyOf", JsonArray(type.types.map { descriptor ->
                buildJsonObject {
                    descriptor.type.toJsonSchema().forEach { (k, v) -> put(k, v) }
                }
            }))
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
internal class AndroidLocalTool(val tool: ToolDescriptor): OpenApiTool {
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
            put("parameters", buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {
                    for (param in allParams) {
                        put(param.name, buildJsonObject {
                            param.type.toJsonSchema().forEach { (k, v) -> put(k, v) }
                            put("description", param.description)
                        })
                    }
                })
                if (tool.requiredParameters.isNotEmpty()) {
                    put("required", JsonArray(tool.requiredParameters.map { JsonPrimitive(it.name) }))
                }
            })
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
