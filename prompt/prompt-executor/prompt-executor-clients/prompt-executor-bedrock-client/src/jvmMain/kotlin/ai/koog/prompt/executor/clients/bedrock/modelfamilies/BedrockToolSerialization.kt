package ai.koog.prompt.executor.clients.bedrock.modelfamilies

import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal object BedrockToolSerialization {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Builds a JSON schema for a tool parameter, including description.
     */
    internal fun buildToolParameterSchema(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", param.description)
        buildTypeSchema(param.type).forEach { (key, value) ->
            put(key, value)
        }
    }

    /**
     * Builds a JSON schema for a parameter type without description.
     * This helper function handles recursive type serialization cleanly.
     */
    private fun buildTypeSchema(type: ToolParameterType): JsonObject = buildJsonObject {
        when (type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")
            ToolParameterType.Null -> put("type", "null")

            is ToolParameterType.Enum -> {
                put("type", "string")
                putJsonArray("enum") { type.entries.forEach { add(json.parseToJsonElement(it)) } }
            }

            is ToolParameterType.List -> {
                put("type", "array")
                put("items", buildTypeSchema(type.itemsType))
            }

            is ToolParameterType.AnyOf -> {
                putJsonArray("anyOf") {
                    addAll(type.types.map { buildToolParameterSchema(it) })
                }
            }

            is ToolParameterType.Object -> {
                put("type", "object")
                putJsonObject("properties") {
                    type.properties.forEach { prop ->
                        put(prop.name, buildToolParameterSchema(prop))
                    }
                }
            }
        }
    }
}
