package ai.koog.prompt.executor.clients.openai.base

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Implementation of the [ToolDescriptorSchemaGenerator] for OpenAI tools.
 */
public open class OpenAICompatibleToolDescriptorSchemaGenerator : ToolDescriptorSchemaGenerator {
    override fun generate(toolDescriptor: ToolDescriptor): JsonObject {
        toolDescriptor.apply {
            return buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    (requiredParameters + optionalParameters).forEach { param ->
                        put(param.name, param.toJsonSchema())
                    }
                }
                putJsonArray("required") {
                    requiredParameters.forEach { param -> add(param.name) }
                }
            }
        }
    }

    private fun ToolParameterDescriptor.toJsonSchema(): JsonObject = buildJsonObject {
        put("description", description)
        fillJsonSchema(type)
    }

    private fun JsonObjectBuilder.fillJsonSchema(type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> put("type", "boolean")
            ToolParameterType.Float -> put("type", "number")
            ToolParameterType.Integer -> put("type", "integer")
            ToolParameterType.String -> put("type", "string")
            ToolParameterType.Null -> put("type", "null")
            is ToolParameterType.Enum -> {
                put("type", "string")
                putJsonArray("enum") {
                    type.entries.forEach { entry -> add(entry) }
                }
            }

            is ToolParameterType.List -> {
                put("type", "array")
                putJsonObject("items") { fillJsonSchema(type.itemsType) }
            }

            is ToolParameterType.Object -> {
                put("type", "object")
                type.additionalProperties?.let { put("additionalProperties", it) }
                putJsonObject("properties") {
                    type.properties.forEach { property ->
                        putJsonObject(property.name) {
                            fillJsonSchema(property.type)
                            put("description", property.description)
                        }
                    }
                }
            }

            is ToolParameterType.AnyOf -> {
                putJsonArray("anyOf") {
                    addAll(
                        type.types.map { parameterType ->
                            parameterType.toJsonSchema()
                        }
                    )
                }
            }
        }
    }
}
