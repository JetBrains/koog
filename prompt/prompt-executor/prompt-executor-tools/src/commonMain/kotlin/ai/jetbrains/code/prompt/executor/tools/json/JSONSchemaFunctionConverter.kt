package ai.jetbrains.code.prompt.executor.tools.json

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.*

fun ToolDescriptor.toJSONSchema(): JsonObject {
    /**
     * Helper function to convert a ToolParameterDescriptor into JSON schema.
     *
     * It maps the declared type to a JSON type. For enums, it creates an "enum" array containing the valid options.
     * For arrays, it recursively converts the items type.
     */
    fun <T> toolParameterToSchema(
        type: ToolParameterType<T>,
        description: String? = null,
        defaultValue: T? = null
    ): JsonObject = buildJsonObject {
        when (type) {
            is ToolParameterType.String -> put("type", "string")
            is ToolParameterType.Integer -> put("type", "integer")
            is ToolParameterType.Float -> put("type", "number")
            is ToolParameterType.Boolean -> put("type", "boolean")
            is ToolParameterType.Enum<*> -> {
                // Assuming the enum entries expose a 'name' property.
                val enumValues = type.entries.map { JsonPrimitive(it.name) }
                put("type", "string")
                put("enum", JsonArray(enumValues))
            }

            is ToolParameterType.List<*> -> {
                put("type", "array")
                put("items", toolParameterToSchema(type.itemsType))
            }
        }

        if (description != null) {
            put("description", JsonPrimitive(description))
        }

        if (defaultValue != null) {
            when (defaultValue) {
                is String -> put("default", JsonPrimitive(defaultValue))
                is Number -> put("default", JsonPrimitive(defaultValue))
                is Boolean -> put("default", JsonPrimitive(defaultValue))
                else -> put("default", JsonPrimitive(defaultValue.toString()))
            }
        }
    }

    // Build the properties object by converting each parameter to its JSON schema.
    val properties = mutableMapOf<String, JsonElement>()

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

    return schemaJson
}