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

    // Helper method to build tool parameter schema
    internal fun buildToolParameterSchema(param: ToolParameterDescriptor): JsonObject = buildJsonObject {
        put("description", param.description)

        when (val type = param.type) {
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
                putJsonObject("items") {
                    when (type.itemsType) {
                        ToolParameterType.Boolean -> put("type", "boolean")
                        ToolParameterType.Float -> put("type", "number")
                        ToolParameterType.Integer -> put("type", "integer")
                        ToolParameterType.String -> put("type", "string")
                        else -> put("type", "string")
                    }
                }
            }

            is ToolParameterType.AnyOf -> {
                putJsonArray("anyOf") {
                    addAll(
                        type.types.map { parameterType ->
                            buildToolParameterSchema(parameterType)
                        }
                    )
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
