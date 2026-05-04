package ai.koog.agents.mcp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.types.Tool as SDKTool

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
        val defs = sdkTool.inputSchema.defs

        // Parse all parameters from the input schema
        val parameters = parseParameters(sdkTool.inputSchema.properties ?: EmptyJsonObject, defs)

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

    private fun parseParameterType(element: JsonObject, defs: JsonObject?, depth: Int = 0): ToolParameterType {
        if (depth > MAX_DEPTH) {
            throw IllegalArgumentException(
                "Maximum recursion depth ($MAX_DEPTH) exceeded. " +
                    "This may indicate a circular reference in the parameter definition."
            )
        }

        // Handle $ref resolution
        val ref = element["\$ref"]?.jsonPrimitive?.content
        if (ref != null) {
            val resolved = resolveRef(ref, defs)
            return parseParameterType(resolved, defs, depth + 1)
        }

        // Extract the type - can be a string or an array of strings (JSON Schema type-array)
        val (typeStr, isNullableTypeArray) = parseTypeInfo(element["type"])

        if (typeStr == null) {
            val anyOf = element["anyOf"]?.jsonArray
            if (anyOf != null) {
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
                            type = parseParameterType(it.jsonObject, defs)
                        )
                    }.toTypedArray()
                )
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
                return ToolParameterType.Enum(enum.map(::enumEntryToString).toTypedArray())
            }

            val title =
                element["title"]?.jsonPrimitive?.content ?: element["description"]?.jsonPrimitive?.content.orEmpty()
            throw IllegalArgumentException("Parameter $title must have type property")
        }

        // Convert the type string to a ToolParameterType
        val parsedType = when (typeStr.lowercase()) {
            // Primitive types
            "string" -> ToolParameterType.String

            "integer" -> ToolParameterType.Integer

            "number" -> ToolParameterType.Float

            "boolean" -> ToolParameterType.Boolean

            "enum" -> ToolParameterType.Enum(
                element.getValue("enum").jsonArray.map(::enumEntryToString).toTypedArray()
            )

            // Array type
            "array" -> {
                val items = element["items"]?.jsonObject
                    ?: throw IllegalArgumentException("Array type parameters must have items property")

                val itemType = parseParameterType(items, defs, depth + 1)

                ToolParameterType.List(itemsType = itemType)
            }

            // Object type
            "object" -> {
                val properties = element["properties"]?.let { properties ->
                    val rawProperties = properties.jsonObject
                    rawProperties.map { (name, property) ->
                        // Description is optional
                        val description = property.jsonObject["description"]?.jsonPrimitive?.content.orEmpty()
                        ToolParameterDescriptor(
                            name,
                            description,
                            parseParameterType(property.jsonObject, defs, depth + 1)
                        )
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
                    when (val ap = element.getValue("additionalProperties")) {
                        // Empty schema `{}` is equivalent to `true`: allow any additional property
                        // without a type constraint. Recursing would fail on missing `type`.
                        is JsonObject -> if (ap.isEmpty()) null else parseParameterType(ap, defs, depth + 1)

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

        // Wrap in AnyOf(Null, type) for type-arrays containing "null", e.g. ["integer", "null"]
        return if (isNullableTypeArray && parsedType != ToolParameterType.Null) {
            ToolParameterType.AnyOf(
                types = arrayOf(
                    ToolParameterDescriptor(name = "", description = "", type = ToolParameterType.Null),
                    ToolParameterDescriptor(name = "", description = "", type = parsedType),
                )
            )
        } else {
            parsedType
        }
    }

    /**
     * Parses the JSON Schema `type` keyword, which can be either a single string
     * (e.g. `"string"`) or an array of strings (e.g. `["string", "null"]`).
     *
     * @return the primary non-null type name (or `"null"` if the array contains only `"null"`,
     *   or `null` if no type is specified), and a flag indicating whether the type-array
     *   included `"null"` (meaning the parameter is nullable).
     */
    private fun parseTypeInfo(typeElement: JsonElement?): TypeInfo = when (typeElement) {
        is JsonPrimitive -> TypeInfo(typeStr = typeElement.content, isNullableTypeArray = false)
        is JsonArray -> {
            val types = typeElement.map { it.jsonPrimitive.content }
            val nonNullTypes = types.filter { it != "null" }
            TypeInfo(
                typeStr = if (nonNullTypes.isEmpty()) "null" else nonNullTypes.first(),
                isNullableTypeArray = types.size != nonNullTypes.size,
            )
        }
        else -> TypeInfo(typeStr = null, isNullableTypeArray = false)
    }

    private data class TypeInfo(val typeStr: String?, val isNullableTypeArray: Boolean)

    /**
     * Converts an individual JSON Schema `enum` entry to its [String] representation.
     *
     * JSON Schema allows mixed value types in `enum` (strings, numbers, booleans, `null`, arrays, objects).
     * Since [ToolParameterType.Enum] stores entries as strings, non-string primitives and composite
     * values are serialized to their canonical JSON form (e.g. `42`, `null`, `["a","b"]`, `{"k":"v"}`),
     * and string primitives are returned unquoted so they remain directly usable.
     */
    private fun enumEntryToString(element: JsonElement): String = when (element) {
        is JsonPrimitive -> if (element.isString) element.content else element.toString()
        else -> element.toString()
    }

    /**
     * Resolves a JSON Schema `$ref` reference against the `$defs` (or `definitions`) block.
     * Only local references (starting with `#/`) are supported.
     */
    private fun resolveRef(ref: String, defs: JsonObject?): JsonObject {
        val path = ref.removePrefix("#/").split("/")
        require(path.size == 2 && path[0] in listOf("\$defs", "definitions")) {
            "Unsupported \$ref format: $ref. Only local references like #/\$defs/TypeName are supported."
        }
        val defName = path[1]
        return defs?.get(defName)?.jsonObject
            ?: throw IllegalArgumentException("Definition '$defName' not found in \$defs for \$ref: $ref")
    }

    private fun parseParameters(properties: JsonObject, defs: JsonObject?): List<ToolParameterDescriptor> {
        return properties.mapNotNull { (name, element) ->
            require(element is JsonObject) { "Parameter $name must be a JSON object" }

            // Extract description from the element
            val description = element["description"]?.jsonPrimitive?.content.orEmpty()

            // Parse the parameter type
            val type = parseParameterType(element, defs)

            // Create a ToolParameterDescriptor
            ToolParameterDescriptor(
                name = name,
                description = description,
                type = type
            )
        }
    }
}
