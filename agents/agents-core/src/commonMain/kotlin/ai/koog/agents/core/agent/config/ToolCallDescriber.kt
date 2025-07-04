 package ai.koog.agents.core.agent.config

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.Message.Assistant
import ai.koog.prompt.message.Message.User
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
     * @param message The tool call message to be described. Must be an instance of Message.Tool.Call.
     * @return A Message instance containing the description of the tool call.
     */
    public fun describeToolCall(message: Message.Tool.Call): Message

    /**
     * Describes the tool result by transforming it into a user-readable message object.
     *
     * @param message The tool result message to be described. It contains the tool call id, tool name, and content details.
     * @return A transformed message representing the description of the tool result.
     */
    public fun describeToolResult(message: Message.Tool.Result): Message

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
         * @param message the tool call message of type [Message.Tool.Call] containing details about the tool invocation,
         * such as tool ID, name, and arguments.
         * @return a [Message.Assistant] containing the serialized JSON representation of the tool call information.
         */
        override fun describeToolCall(message: Message.Tool.Call): Message {
            return Assistant(
                content = Json.encodeToString(
                    buildJsonObject {
                        message.id?.let { put("tool_call_id", JsonPrimitive(it)) }
                        put("tool_name", JsonPrimitive(message.tool))
                        put("tool_args", message.contentJson)
                    }
                ),
                metaInfo = message.metaInfo
            )
        }

        /**
         * Creates a user message containing a structured JSON representation
         * of a tool result including its ID, tool name, and result content.
         *
         * @param message The tool result message containing the tool's ID, name, and content.
         * @return A User message with a JSON-encoded representation of the tool result.
         */
        override fun describeToolResult(message: Message.Tool.Result): Message {
            return User(
                content = Json.encodeToString(
                    buildJsonObject {
                        message.id?.let { put("tool_call_id", JsonPrimitive(it)) }
                        put("tool_name", JsonPrimitive(message.tool))
                        put("tool_result", JsonPrimitive(message.content))
                    }
                ),
                metaInfo = message.metaInfo
            )
        }
    }
}
