package ai.koog.prompt.executor.litertlm.client

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Functional interface for executing tools.
 *
 * Implementations should handle tool execution synchronously or use appropriate
 * blocking mechanisms for async operations.
 */
public fun interface ToolExecutor {
    /**
     * Executes a tool with the given name and arguments.
     *
     * @param toolName The name of the tool to execute.
     * @param arguments The parsed arguments as a map.
     * @return The result of the tool execution as a string.
     */
    public fun execute(toolName: String, arguments: Map<String, Any?>): String
}

/**
 * Bridges Koog's [ToolDescriptor] to LiteRT-LM's annotation-based tool system.
 *
 * This class acts as a dispatcher that exposes a single `@Tool` annotated method
 * to LiteRT-LM. When the model calls this tool, it delegates to the appropriate
 * Koog tool via the provided [ToolExecutor].
 *
 * ## Usage
 *
 * ```kotlin
 * val tools = listOf(weatherTool.descriptor, calculatorTool.descriptor)
 * val bridge = LiteRTLMToolBridge(tools) { name, args ->
 *     when (name) {
 *         "get_weather" -> getWeather(args["city"] as String)
 *         "calculate" -> calculate(args["expression"] as String)
 *         else -> "Unknown tool: $name"
 *     }
 * }
 *
 * // Pass bridge to conversation
 * val config = ConversationConfig(tools = listOf(bridge))
 * ```
 *
 * @param tools The list of Koog tool descriptors to expose.
 * @param executor The callback to execute when a tool is called.
 */
public class LiteRTLMToolBridge(
    private val tools: List<ToolDescriptor>,
    private val executor: ToolExecutor,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The tool method that LiteRT-LM will call.
     *
     * This is a dispatcher that routes to the appropriate Koog tool based on the
     * tool name. The model will see this as the only available tool, but the
     * system prompt includes descriptions of all available tools.
     *
     * @param toolName The name of the tool to call (must match a tool in [tools]).
     * @param argsJson JSON string containing the arguments for the tool.
     * @return The result of the tool execution, or an error message.
     */
    @Tool(description = "Execute a tool. Use the tool name and provide arguments as JSON.")
    public fun callTool(
        @ToolParam(description = "The exact name of the tool to call")
        toolName: String,
        @ToolParam(description = "JSON object with the tool's arguments")
        argsJson: String,
    ): String {
        val tool = tools.find { it.name == toolName }
            ?: return buildErrorResponse(
                "Unknown tool: '$toolName'. Available tools: ${tools.joinToString { it.name }}"
            )

        val arguments = try {
            parseArguments(argsJson, tool)
        } catch (e: Exception) {
            return buildErrorResponse("Invalid arguments for '$toolName': ${e.message}")
        }

        return try {
            executor.execute(toolName, arguments)
        } catch (e: Exception) {
            buildErrorResponse("Tool execution failed: ${e.message}")
        }
    }

    /**
     * Generates a system prompt section describing all available tools.
     *
     * Include this in the system prompt so the model knows what tools are available
     * and how to call them.
     *
     * @return A formatted string describing all tools and their parameters.
     */
    public fun generateToolsPrompt(): String = buildString {
        appendLine("## Available Tools")
        appendLine()
        appendLine("You can use the following tools by calling `callTool` with the tool name and arguments:")
        appendLine()

        for (tool in tools) {
            appendLine("### ${tool.name}")
            appendLine(tool.description)
            appendLine()

            val allParams = tool.requiredParameters + tool.optionalParameters
            if (allParams.isNotEmpty()) {
                appendLine("**Parameters:**")
                for (param in tool.requiredParameters) {
                    appendLine("- `${param.name}` (${formatType(param.type)}, required): ${param.description}")
                }
                for (param in tool.optionalParameters) {
                    appendLine("- `${param.name}` (${formatType(param.type)}, optional): ${param.description}")
                }
                appendLine()
            }

            // Example usage
            appendLine("**Example:**")
            appendLine("```json")
            appendLine(generateExampleCall(tool))
            appendLine("```")
            appendLine()
        }
    }

    private fun parseArguments(argsJson: String, tool: ToolDescriptor): Map<String, Any?> {
        if (argsJson.isBlank() || argsJson == "{}") {
            return emptyMap()
        }

        val jsonObject = json.parseToJsonElement(argsJson).jsonObject
        val result = mutableMapOf<String, Any?>()

        // Parse required parameters
        for (param in tool.requiredParameters) {
            val value = jsonObject[param.name]
                ?: throw IllegalArgumentException("Missing required parameter: ${param.name}")
            result[param.name] = convertJsonToKotlin(value, param.type)
        }

        // Parse optional parameters
        for (param in tool.optionalParameters) {
            val value = jsonObject[param.name]
            if (value != null && value !is JsonNull) {
                result[param.name] = convertJsonToKotlin(value, param.type)
            }
        }

        return result
    }

    private fun convertJsonToKotlin(element: JsonElement, type: ToolParameterType): Any? {
        return when (type) {
            is ToolParameterType.String -> element.jsonPrimitive.content
            is ToolParameterType.Null -> null
            is ToolParameterType.Integer ->
                element.jsonPrimitive.intOrNull
                    ?: element.jsonPrimitive.longOrNull?.toInt()
                    ?: element.jsonPrimitive.content.toIntOrNull()
            is ToolParameterType.Float ->
                element.jsonPrimitive.doubleOrNull
                    ?: element.jsonPrimitive.content.toDoubleOrNull()
            is ToolParameterType.Boolean ->
                element.jsonPrimitive.booleanOrNull
                    ?: element.jsonPrimitive.content.toBooleanStrictOrNull()
            is ToolParameterType.List -> {
                element.jsonArray.map { item ->
                    convertJsonToKotlin(item, type.itemsType)
                }
            }
            is ToolParameterType.Object -> {
                val obj = element.jsonObject
                type.properties.associate { prop ->
                    prop.name to obj[prop.name]?.let { convertJsonToKotlin(it, prop.type) }
                }
            }
            is ToolParameterType.Enum -> element.jsonPrimitive.content
            is ToolParameterType.AnyOf -> {
                for (descriptor in type.types) {
                    val value = runCatching { convertJsonToKotlin(element, descriptor.type) }.getOrNull()
                    if (value != null) {
                        return value
                    }
                }
                element.jsonPrimitive.content
            }
        }
    }

    private fun formatType(type: ToolParameterType): String = when (type) {
        is ToolParameterType.String -> "string"
        is ToolParameterType.Null -> "null"
        is ToolParameterType.Integer -> "integer"
        is ToolParameterType.Float -> "float"
        is ToolParameterType.Boolean -> "boolean"
        is ToolParameterType.List -> "array<${formatType(type.itemsType)}>"
        is ToolParameterType.Object -> "object"
        is ToolParameterType.Enum -> "enum(${type.entries.joinToString("|")})"
        is ToolParameterType.AnyOf -> "anyOf(${type.types.joinToString { formatType(it.type) }})"
    }

    private fun generateExampleCall(tool: ToolDescriptor): String {
        val exampleArgs = buildMap {
            for (param in tool.requiredParameters) {
                put(param.name, generateExampleValue(param.type))
            }
        }
        return """{"toolName": "${tool.name}", "argsJson": "${json.encodeToString(JsonObject.serializer(), JsonObject(exampleArgs.mapValues { jsonValueOf(it.value) }))}"}"""
    }

    private fun generateExampleValue(type: ToolParameterType): Any = when (type) {
        is ToolParameterType.String -> "example"
        is ToolParameterType.Null -> "null"
        is ToolParameterType.Integer -> 42
        is ToolParameterType.Float -> 3.14
        is ToolParameterType.Boolean -> true
        is ToolParameterType.List -> listOf(generateExampleValue(type.itemsType))
        is ToolParameterType.Object -> mapOf("key" to "value")
        is ToolParameterType.Enum -> type.entries.firstOrNull() ?: "value"
        is ToolParameterType.AnyOf -> generateExampleValue(type.types.first().type)
    }

    private fun jsonValueOf(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is List<*> -> JsonArray(
            value.map { item ->
                jsonValueOf(item)
            }
        )
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) ->
                k.toString() to jsonValueOf(v)
            }
        )
        else -> JsonPrimitive(value.toString())
    }

    private fun buildErrorResponse(message: String): String {
        return """{"error": "$message"}"""
    }
}
