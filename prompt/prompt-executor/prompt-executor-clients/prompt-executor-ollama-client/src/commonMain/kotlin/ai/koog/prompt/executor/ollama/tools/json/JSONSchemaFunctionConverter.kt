package ai.koog.prompt.executor.ollama.tools.json

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts the current ToolDescriptor instance into a JSON Schema representation.
 *
 * This function generates a JSON object that conforms to the schema of the tool, including all required and optional
 * parameters with their respective types and descriptions. The schema defines the tool structure in a JSON-friendly
 * format for validation or documentation purposes.
 *
 * @return A JsonObject representing the JSON Schema for the current ToolDescriptor instance.
 */
public fun ToolDescriptor.toJSONSchema(): JsonObject {
    /**
     * Helper function to convert a ToolParameterDescriptor into JSON schema.
     *
     * It maps the declared type to a JSON type. For enums, it creates an "enum" array containing the valid options.
     * For arrays, it recursively converts the items type.
     */
    fun toolParameterToSchema(
        type: ToolParameterType,
        description: String? = null,
    ): JsonObject = buildJsonObject {
        when (type) {
            is ToolParameterType.String -> put("type", "string")
            is ToolParameterType.Integer -> put("type", "integer")
            is ToolParameterType.Float -> put("type", "number")
            is ToolParameterType.Boolean -> put("type", "boolean")
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

    // Build the properties object by converting each parameter to its JSON schema.
    val properties: JsonObject = buildJsonObject {
        (requiredParameters + optionalParameters)
            .map { param -> put(param.name, toolParameterToSchema(param.type, param.description)) }
    }

    // Build the outer JSON schema.
    val schemaJson = buildJsonObject {
        put("title", JsonPrimitive(name))
        put("description", JsonPrimitive(description))
        put("type", JsonPrimitive("object"))
        put("properties", JsonObject(properties))
        put("required", JsonArray(requiredParameters.map { JsonPrimitive(it.name) }))
    }

    return schemaJson
}
