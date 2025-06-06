package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.*
import io.modelcontextprotocol.kotlin.sdk.Tool as SDKTool

/**
 * Parsers tool definition from MCP SDK to our tool descriptor format.
 */
public interface McpToolDescriptorParser {
    public fun parse(sdkTool: SDKTool): ToolDescriptor
}

/**
 * Default implementation of [McpToolDescriptorParser].
 */
public object DefaultMcpToolDescriptorParser : McpToolDescriptorParser {
    /**
     * Parses an MCP SDK Tool definition into tool descriptor format.
     *
     * This method extracts tool information (name, description, parameters) from an MCP SDK Tool
     * and converts it into a ToolDescriptor that can be used by the agent framework.
     *
     * @param sdkTool The MCP SDK Tool to parse.
     * @return A ToolDescriptor representing the MCP tool.
     */
    override fun parse(sdkTool: SDKTool): ToolDescriptor {
        // Parse all parameters from the input schema
        val parameters = parseParameters(sdkTool.inputSchema.properties)

        // Get the list of required parameters
        val requiredParameters = sdkTool.inputSchema.required ?: emptyList()

        // Create a ToolDescriptor
        return ToolDescriptor(
            name = sdkTool.name,
            description = sdkTool.description.orEmpty(),
            requiredParameters = parameters.filter { requiredParameters.contains(it.name) },
            optionalParameters = parameters.filter { !requiredParameters.contains(it.name) },
        )
    }

    private fun parseParameterType(element: JsonObject): ToolParameterType {
        // Extract the type string from the JSON object
        val typeStr = element["type"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Parameter type must have type property")

        // Convert the type string to a ToolParameterType
        return when (typeStr.lowercase()) {
            // Primitive types
            "string" -> ToolParameterType.String
            "integer" -> ToolParameterType.Integer
            "number" -> ToolParameterType.Float
            "boolean" -> ToolParameterType.Boolean
            "enum" -> ToolParameterType.Enum(
                element.getValue("enum").jsonArray.map { it.jsonPrimitive.content }.toTypedArray()
            )

            // Array type
            "array" -> {
                val items = element["items"]?.jsonObject
                    ?: throw IllegalArgumentException("Array type parameters must have items property")

                val itemType = parseParameterType(items)

                ToolParameterType.List(itemsType = itemType)
            }

            // Object type
            "object" -> {
                val properties = element["properties"]?.let { properties ->
                    val rawProperties = properties.jsonObject
                    rawProperties.map { (name, property) ->
                        // Description is optional
                        val description = element["description"]?.jsonPrimitive?.content.orEmpty()
                        ToolParameterDescriptor(name, description, parseParameterType(property.jsonObject))
                    }
                } ?: emptyList()

                val required = element["required"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()


                val additionalProperties = if ("additionalProperties" in element) {
                    when (element.getValue("additionalProperties")) {
                        is JsonPrimitive -> element.getValue("additionalProperties").jsonPrimitive.boolean
                        is JsonObject -> true
                        else -> null
                    }
                } else {
                    null
                }

                val additionalPropertiesType = if ("additionalProperties" in element) {
                    when (element.getValue("additionalProperties")) {
                        is JsonObject -> parseParameterType(element.getValue("additionalProperties").jsonObject)
                        else -> null
                    }
                } else {
                    null
                }

                ToolParameterType.Object(
                    properties = properties,
                    requiredProperties = required,
                    additionalPropertiesType = additionalPropertiesType,
                    additionalProperties = additionalProperties
                )
            }

            // Unsupported type
            else -> throw IllegalArgumentException("Unsupported parameter type: $typeStr")
        }
    }

    private fun parseParameters(properties: JsonObject): List<ToolParameterDescriptor> {
        return properties.mapNotNull { (name, element) ->
            require(element is JsonObject) { "Parameter $name must be a JSON object" }

            // Extract description from the element
            val description = element["description"]?.jsonPrimitive?.content.orEmpty()

            // Parse the parameter type
            val type = parseParameterType(element)

            // Create a ToolParameterDescriptor
            ToolParameterDescriptor(
                name = name, description = description, type = type
            )
        }
    }
}
