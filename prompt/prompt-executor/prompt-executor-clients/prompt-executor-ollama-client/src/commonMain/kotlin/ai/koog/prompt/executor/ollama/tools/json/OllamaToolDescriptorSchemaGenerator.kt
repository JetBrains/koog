package ai.koog.prompt.executor.ollama.tools.json

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.serialization.ToolDescriptorSchemaGenerator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Converts a list of ToolDescriptor objects into a JSON schema representation for Ollama.
 */
public class OllamaToolDescriptorSchemaGenerator : ToolDescriptorSchemaGenerator {
    override fun generate(toolDescriptor: ToolDescriptor): JsonObject {
        // Build the properties object by converting each parameter to its JSON schema.
        val properties: JsonObject = buildJsonObject {
            (toolDescriptor.requiredParameters + toolDescriptor.optionalParameters)
                .map { param -> put(param.name, toolParameterToSchema(param.type, param.description)) }
        }

        // Build the outer JSON schema.
        val schemaJson = buildJsonObject {
            put("title", JsonPrimitive(toolDescriptor.name))
            put("description", JsonPrimitive(toolDescriptor.description))
            put("type", JsonPrimitive("object"))
            put("properties", JsonObject(properties))
            put("required", JsonArray(toolDescriptor.requiredParameters.map { JsonPrimitive(it.name) }))
        }

        return schemaJson
    }

    private fun toolParameterToSchema(
        type: ToolParameterType,
        description: String? = null,
    ): JsonObject = buildJsonObject {
        when (type) {
            is ToolParameterType.String -> put("type", "string")
            is ToolParameterType.Integer -> put("type", "integer")
            is ToolParameterType.Float -> put("type", "number")
            is ToolParameterType.Boolean -> put("type", "boolean")
            is ToolParameterType.Null -> put("type", "null")
            is ToolParameterType.Enum -> {
                // Assuming the enum entries expose a 'name' property.
                val enumValues = type.entries.map { JsonPrimitive(it) }
                put("type", "string")
                put("enum", JsonArray(enumValues))
            }

            is ToolParameterType.List -> {
                put("type", "array")
                put("items", toolParameterToSchema(type.itemsType))
            }

            is ToolParameterType.AnyOf -> {
                putJsonArray("anyOf") {
                    addAll(
                        type.types.map { parameterType ->
                            toolParameterToSchema(parameterType.type, parameterType.description)
                        }
                    )
                }
            }

            is ToolParameterType.Object -> {
                put("type", JsonPrimitive("object"))

                put(
                    "properties",
                    buildJsonObject {
                        type.properties.forEach { property ->
                            put(property.name, toolParameterToSchema(property.type, property.description))
                        }
                    }
                )

                put("required", JsonArray(type.requiredProperties.map { JsonPrimitive(it) }))
            }
        }

        if (description != null) {
            put("description", description)
        }
    }
}
