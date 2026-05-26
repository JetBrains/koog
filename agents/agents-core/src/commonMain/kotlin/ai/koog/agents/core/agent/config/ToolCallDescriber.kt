package ai.koog.agents.core.agent.config

import ai.koog.prompt.message.MessagePart
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Describes the way to reformat tool call/tool result messages,
 * in case real tool call/tool result messages cannot be used
 */
public interface ToolCallDescriber {
    /**
     * Composes a description of a tool call message.
     *
     * @param message The tool call message to be described. Must be an instance of MessagePart.Tool.Call.
     * @return A Message instance containing the description of the tool call.
     */
    public fun describeToolCall(message: MessagePart.Tool.Call): String

    /**
     * Describes the tool result by transforming it into a user-readable string.
     *
     * @param message The tool result message to be described. It contains the tool call id, tool name, and content details.
     * @return A transformed message representing the description of the tool result.
     */
    public fun describeToolResult(message: MessagePart.Tool.Result): String

    /**
     * JSON object implementing the `ToolCallDescriber` interface.
     * This object is responsible for describing tool calls and results by converting them into a structured JSON-based format.
     */
    public object JSON : ToolCallDescriber {
        /**
         * A configuration of the kotlinx.serialization.Json instance tailored for serializing and
         * deserializing JSON data.
         *
         * This specific instance has the following options configured:
         * - `encodeDefaults` set to `true`: Ensures that default values are encoded during serialization.
         * - `explicitNulls` set to `false`: Avoids including `null` values explicitly in the resulting JSON output.
         *
         * It is used internally for encoding and decoding JSON representations of tool-related data.
         */
        private val Json = Json {
            encodeDefaults = true
            explicitNulls = false
        }

        /**
         * Formats a tool call message into a standardized Message.Assistant response.
         *
         * @param message the tool call message of type [MessagePart.Tool.Call] containing details about the tool invocation,
         * such as tool ID, name, and arguments.
         * @return a string containing the serialized JSON representation of the tool call information.
         */
        override fun describeToolCall(message: MessagePart.Tool.Call): String {
            return Json.encodeToString(
                buildJsonObject {
                    message.id?.let { put("tool_call_id", JsonPrimitive(it)) }
                    put("tool_name", JsonPrimitive(message.tool))
                    runCatching { message.argsJson }
                        .onSuccess { put("tool_args", it) }
                        .onFailure { e ->
                            put(
                                "tool_args_error",
                                JsonPrimitive("Failed to parse tool arguments: ${e::class.simpleName}: ${e.message}")
                            )
                        }
                }
            )
        }

        /**
         * Creates a user message containing a structured JSON representation
         * of a tool result including its ID, tool name, and result content.
         *
         * @param message The tool result message containing the tool's ID, name, and content.
         * @return A User message with a JSON-encoded representation of the tool result.
         */
        override fun describeToolResult(message: MessagePart.Tool.Result): String {
            return Json.encodeToString(
                buildJsonObject {
                    message.id?.let { put("tool_call_id", JsonPrimitive(it)) }
                    put("tool_name", JsonPrimitive(message.tool))
                    put("tool_result", JsonPrimitive(message.output))
                }
            )
        }
    }
}
