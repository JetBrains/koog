package ai.koog.prompt.executor.cached

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Convert a ToolDescriptor to a JsonObject representation.
 */
// TODO: duplicated code?
// TODO: yep, should we remove it?
private fun ToolDescriptor.toJSONSchema(): JsonObject {
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
                put("type", "object")
                put(
                    "properties",
                    buildJsonObject {
                        type.properties.forEach { property ->
                            put(
                                property.name,
                                buildJsonObject {
                                    toolParameterToSchema(property.type)
                                    put("description", property.description)
                                }
                            )
                        }
                    }
                )
            }
        }

        if (description != null) {
            put("description", JsonPrimitive(description))
        }
    }

    // Build the properties object by converting each parameter to its JSON schema.
    val properties = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()

    // Process required parameters.
    for (param in requiredParameters) {
        properties[param.name] = toolParameterToSchema(param.type)
    }
    // Process optional parameters.
    for (param in optionalParameters) {
        properties[param.name] = toolParameterToSchema(param.type)
    }

    // Build the outer JSON schema.
    val schemaJson = buildJsonObject {
        put("title", JsonPrimitive(name))
        put("description", JsonPrimitive(description))
        put("type", JsonPrimitive("object"))
        put("properties", JsonObject(properties))
        put("required", JsonArray(requiredParameters.map { JsonPrimitive(it.name) }))
    }

    // Return the JSON schema
    return schemaJson
}
