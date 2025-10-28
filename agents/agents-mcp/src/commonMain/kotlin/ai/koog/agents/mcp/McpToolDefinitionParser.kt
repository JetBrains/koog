package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.Tool as SDKTool

/**
 * Parsers tool definition from MCP SDK to our tool descriptor format.
 */
public interface McpToolDescriptorParser {
    /**
     * Parses an SDK tool representation into a standardized ToolDescriptor format.
     *
     * @param sdkTool The SDKTool instance containing tool information to be parsed.
     * @return The parsed ToolDescriptor, representing the tool in a standardized format.
     */
    public fun parse(sdkTool: SDKTool): ToolDescriptor
}

/**
 * Default implementation of [McpToolDescriptorParser].
 */
public object DefaultMcpToolDescriptorParser : McpToolDescriptorParser {
    // Maximum depth of recursive parsing
    private const val MAX_DEPTH = 30

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

    private fun parseParameterType(element: JsonObject, depth: Int = 0): ToolParameterType {
        if (depth > MAX_DEPTH) {
            throw IllegalArgumentException(
                "Maximum recursion depth ($MAX_DEPTH) exceeded. " +
                    "This may indicate a circular reference in the parameter definition."
            )
        }

        // Extract the type string from the JSON object
        val typeStr = element["type"]?.jsonPrimitive?.content

        if (typeStr == null) {
            val anyOf = element["anyOf"]?.jsonArray
            if (anyOf != null) {
                val types = anyOf.map { it.jsonObject["type"]?.jsonPrimitive?.content }
                /**
                 * Special case for nullable types.
                 * Schema example:
                 * {
                 *   "nullableParam": {
                 *     "anyOf": [
                 *       { "type": "string" },
                 *       { "type": "null" }
                 *     ],
                 *     "title": "Nullable string parameter"
                 *   }
                 * }
                 */
                if (anyOf.size == 2 && types.contains("null")) {
                    val nonNullType = anyOf.first {
                        it.jsonObject["type"]?.jsonPrimitive?.content != "null"
                    }.jsonObject
                    return parseParameterType(nonNullType, depth + 1)
                } else {
                    /**
                     * anyOf with multiple types.
                     * Schema example:
                     * {
                     *   "anyOfParam": {
                     *     "anyOf": [
                     *       { "type": "string" },
                     *       { "type": "number" }
                     *     ],
                     *     "title": "string or number parameter"
                     *   }
                     * }
                     */
                    return ToolParameterType.AnyOf(
                        types = anyOf.map { it.jsonObject }.map {
                            ToolParameterDescriptor(
                                name = "",
                                description = it["description"]?.jsonPrimitive?.content.orEmpty(),
                                type = parseParameterType(it.jsonObject)
                            )
                        }.toTypedArray()
                    )
                }
            }

            /**
             * Special case for enum string types.
             * Schema example:
             * {
             *   "enumParam": {
             *     "enum": [
             *       "value1",
             *       "value2"
             *     ],
             *     "title": "Enum string parameter"
             *   }
             * }
             */
            val enum = element["enum"]?.jsonArray
            if (enum != null && enum.isNotEmpty()) {
                return ToolParameterType.Enum(enum.map { it.jsonPrimitive.content }.toTypedArray())
            }

            val title =
                element["title"]?.jsonPrimitive?.content ?: element["description"]?.jsonPrimitive?.content.orEmpty()
            throw IllegalArgumentException("Parameter $title must have type property")
        }

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

                val itemType = parseParameterType(items, depth + 1)

                ToolParameterType.List(itemsType = itemType)
            }

            // Object type
            "object" -> {
                val properties = element["properties"]?.let { properties ->
                    val rawProperties = properties.jsonObject
                    rawProperties.map { (name, property) ->
                        // Description is optional
                        val description = property.jsonObject["description"]?.jsonPrimitive?.content.orEmpty()
                        ToolParameterDescriptor(name, description, parseParameterType(property.jsonObject, depth + 1))
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
                        is JsonObject -> parseParameterType(
                            element.getValue("additionalProperties").jsonObject,
                            depth + 1
                        )
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

            "null" -> ToolParameterType.Null

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
                name = name,
                description = description,
                type = type
            )
        }
    }
}
