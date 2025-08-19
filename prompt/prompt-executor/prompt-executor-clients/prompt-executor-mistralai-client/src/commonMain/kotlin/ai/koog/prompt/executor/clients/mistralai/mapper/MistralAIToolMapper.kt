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

    private object JsonSchemaKeys {
        const val TYPE = "type"
        const val DESCRIPTION = "description"
        const val ENUM = "enum"
        const val ITEMS = "items"
        const val PROPERTIES = "properties"
        const val REQUIRED = "required"
        const val ADDITIONAL_PROPERTIES = "additionalProperties"
    }

    private object JsonSchemaTypes {
        const val BOOLEAN = "boolean"
        const val NUMBER = "number"
        const val INTEGER = "integer"
        const val STRING = "string"
        const val ARRAY = "array"
        const val OBJECT = "object"
    }

    fun createMistralAITools(toolDescriptors: List<ToolDescriptor>): List<MistralAITool> {
        return toolDescriptors.map { descriptor ->
            val parameterSchemas = buildParameterSchemas(descriptor)

            MistralAITool(
                function = MistralAIFunction(
                    name = descriptor.name,
                    description = descriptor.description,
                    parameters = MistralAIToolParamsSpecification(
                        properties = JsonObject(parameterSchemas),
                        required = descriptor.requiredParameters.map { it.name }
                    )
                )
            )
        }
    }

    private fun buildParameterSchemas(descriptor: ToolDescriptor): Map<String, JsonElement> {
        val allParameters = descriptor.requiredParameters + descriptor.optionalParameters
        return allParameters.associate { parameter ->
            val schema = convertToJsonSchema(parameter.type)
            val schemaWithDescription = schema.toMutableMap().apply {
                put(JsonSchemaKeys.DESCRIPTION, JsonPrimitive(parameter.description))
            }
            parameter.name to JsonObject(schemaWithDescription)
        }
    }

    /**
     * Converts a ToolParameterType to its corresponding JSON Schema representation
     */
    private fun convertToJsonSchema(parameterType: ToolParameterType): JsonObject {
        return when (parameterType) {
            ToolParameterType.Boolean -> createPrimitiveSchema(JsonSchemaTypes.BOOLEAN)
            ToolParameterType.Float -> createPrimitiveSchema(JsonSchemaTypes.NUMBER)
            ToolParameterType.Integer -> createPrimitiveSchema(JsonSchemaTypes.INTEGER)
            ToolParameterType.String -> createPrimitiveSchema(JsonSchemaTypes.STRING)

            is ToolParameterType.Enum -> createEnumSchema(parameterType.entries)
            is ToolParameterType.List -> createArraySchema(parameterType.itemsType)
            is ToolParameterType.Object -> createObjectSchema(parameterType)
        }
    }

    private fun createPrimitiveSchema(type: String): JsonObject {
        return JsonObject(mapOf(JsonSchemaKeys.TYPE to JsonPrimitive(type)))
    }

    private fun createEnumSchema(enumEntries: Array<String>): JsonObject {
        return JsonObject(
            mapOf(
                JsonSchemaKeys.TYPE to JsonPrimitive(JsonSchemaTypes.STRING),
                JsonSchemaKeys.ENUM to JsonArray(enumEntries.map { JsonPrimitive(it.lowercase()) })
            )
        )
    }

    private fun createArraySchema(itemsType: ToolParameterType): JsonObject {
        return JsonObject(
            mapOf(
                JsonSchemaKeys.TYPE to JsonPrimitive(JsonSchemaTypes.ARRAY),
                JsonSchemaKeys.ITEMS to convertToJsonSchema(itemsType)
            )
        )
    }

    private fun createObjectSchema(objectType: ToolParameterType.Object): JsonObject {
        val propertySchemas = objectType.properties.associate { property ->
            val propertySchema = convertToJsonSchema(property.type).toMutableMap()
            propertySchema[JsonSchemaKeys.DESCRIPTION] = JsonPrimitive(property.description)
            property.name to JsonObject(propertySchema)
        }

        val schemaMap = mutableMapOf<String, JsonElement>().apply {
            put(JsonSchemaKeys.TYPE, JsonPrimitive(JsonSchemaTypes.OBJECT))
            put(JsonSchemaKeys.PROPERTIES, JsonObject(propertySchemas))

            if (objectType.requiredProperties.isNotEmpty()) {
                put(JsonSchemaKeys.REQUIRED, JsonArray(objectType.requiredProperties.map(::JsonPrimitive)))
            }

            put(JsonSchemaKeys.ADDITIONAL_PROPERTIES, JsonPrimitive(objectType.additionalProperties ?: false))
        }

        return JsonObject(schemaMap)
    }
}