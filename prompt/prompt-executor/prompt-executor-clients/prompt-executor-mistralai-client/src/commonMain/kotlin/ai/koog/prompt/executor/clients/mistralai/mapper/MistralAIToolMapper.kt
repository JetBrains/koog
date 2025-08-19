package ai.koog.prompt.executor.clients.mistralai.mapper

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIFunction
import ai.koog.prompt.executor.clients.mistralai.model.MistralAITool
import ai.koog.prompt.executor.clients.mistralai.model.MistralAIToolParamsSpecification
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object MistralAIToolMapper {

    fun createMistralAITools(tools: List<ToolDescriptor>): List<MistralAITool> {
        return tools.map { tool ->
            val properties = mutableMapOf<String, JsonElement>()
            (tool.requiredParameters + tool.optionalParameters).forEach { param ->
                val typeMap = getTypeMapForParameter(param.type)

                properties[param.name] = JsonObject(
                    mapOf("description" to JsonPrimitive(param.description)) + typeMap
                )
            }

            MistralAITool(
                function = MistralAIFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = MistralAIToolParamsSpecification(
                        properties = JsonObject(properties),
                        required = tool.requiredParameters.map { it.name }
                    )
                )
            )
        }
    }

    /**
     * Helper function to get the type map for a parameter type without using smart casting
     */
    private fun getTypeMapForParameter(type: ToolParameterType): JsonObject {
        return when (type) {
            ToolParameterType.Boolean -> JsonObject(mapOf("type" to JsonPrimitive("boolean")))
            ToolParameterType.Float -> JsonObject(mapOf("type" to JsonPrimitive("number")))
            ToolParameterType.Integer -> JsonObject(mapOf("type" to JsonPrimitive("integer")))
            ToolParameterType.String -> JsonObject(mapOf("type" to JsonPrimitive("string")))
            is ToolParameterType.Enum -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("string"),
                    "enum" to JsonArray(type.entries.map { JsonPrimitive(it.lowercase()) })
                )
            )

            is ToolParameterType.List -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to getTypeMapForParameter(type.itemsType)
                )
            )

            is ToolParameterType.Object -> {
                // Create properties map with proper type information
                val propertiesMap = mutableMapOf<String, JsonElement>()

                for (prop in type.properties) {
                    // Get type information for the property
                    val typeInfo = getTypeMapForParameter(prop.type)

                    // Create a map with all type properties and description
                    val propMap = mutableMapOf<String, JsonElement>()
                    for (entry in typeInfo.entries) {
                        propMap[entry.key] = entry.value
                    }
                    propMap["description"] = JsonPrimitive(prop.description)

                    // Add to properties map
                    propertiesMap[prop.name] = JsonObject(propMap)
                }

                // Create the final object schema
                val objectMap = mutableMapOf<String, JsonElement>()
                objectMap["type"] = JsonPrimitive("object")
                objectMap["properties"] = JsonObject(propertiesMap)

                // Add required field if requiredProperties is not empty
                if (type.requiredProperties.isNotEmpty()) {
                    objectMap["required"] = JsonArray(type.requiredProperties.map { JsonPrimitive(it) })
                }

                // Add additionalProperties for strict validation
                objectMap["additionalProperties"] = JsonPrimitive(type.additionalProperties ?: false)

                JsonObject(objectMap)
            }
        }
    }
}