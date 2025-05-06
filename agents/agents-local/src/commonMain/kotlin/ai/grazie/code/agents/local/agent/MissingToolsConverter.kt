package ai.grazie.code.agents.local.agent

import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.message.Message.Assistant
import ai.jetbrains.code.prompt.message.Message.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Determines how the tool calls which are present in the prompt, but whose definitions are not present in the request,
 * are converted when sending to the Grazie API.
 *
 * Missing tool definitions usually occur when different sets of tools are used between stages/subgraphs,
 * and the same prompt history is used without compression.
 *
 * @property format Formatter used to convert tool calls
 */
abstract class MissingToolsConversionStrategy(val format: ToolCallDescriber) {
    abstract fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt

    fun convertMessage(message: Message): Message {
        return when (message) {
            is Message.Tool.Call -> format.describeToolCall(message)
            is Message.Tool.Result -> format.describeToolResult(message)
            else -> message
        }
    }

    /**
     * Replace all real tool call and response messages with their dumps to the specified format,
     * and use them as plaintext messages.
     */
    class All(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            return prompt.copy(messages = prompt.messages.map { convertMessage(it) })
        }
    }

    /**
     * Replace only missing real tool call and response messages with their dumps to the specified format,
     * and use them as plaintext messages. The tool calls whose definitions are not missing, will be left
     * as real tool calls and responses.
     */
    class Missing(format: ToolCallDescriber) : MissingToolsConversionStrategy(format) {
        override fun convertPrompt(prompt: Prompt, tools: List<ToolDescriptor>): Prompt {
            val toolNames = tools.map { it.name }
            return prompt.copy(messages = prompt.messages.map { message ->
                if (message is Message.Tool) {
                    if (message.tool !in toolNames) {
                        return@map convertMessage(message)
                    }
                }
                return@map message
            })
        }
    }
}

/**
 * Describes the way to reformat tool call/tool result messages,
 * in case real tool call/tool result messages cannot be used
 */
interface ToolCallDescriber {
    /**
     * Composes a description of a tool call message.
     *
     * @param message The tool call message to be described. Must be an instance of Message.Tool.Call.
     * @return A Message instance containing the description of the tool call.
     */
    fun describeToolCall(message: Message.Tool.Call): Message
    /**
     * Describes the tool result by transforming it into a user-readable message object.
     *
     * @param message The tool result message to be described. It contains the tool call id, tool name, and content details.
     * @return A transformed message representing the description of the tool result.
     */
    fun describeToolResult(message: Message.Tool.Result): Message

    /**
     * JSON object implementing the `ToolCallDescriber` interface.
     * This object is responsible for describing tool calls and results by converting them into a structured JSON-based format.
     */
    object JSON : ToolCallDescriber {
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
                Json.encodeToString(
                    buildJsonObject {
                        message.id ?: put("tool_call_id", JsonPrimitive(message.id))
                        put("tool_name", JsonPrimitive(message.tool))
                        put("tool_args", message.contentJson)
                    }
                )
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
                Json.encodeToString(
                    buildJsonObject {
                        message.id ?: put("tool_call_id", JsonPrimitive(message.id))
                        put("tool_name", JsonPrimitive(message.tool))
                        put("tool_result", JsonPrimitive(message.content))
                    }
                )
            )
        }
    }
}

/**
 * Does not change tool calls, leaving real tool call messages
 */
internal object Original : ToolCallDescriber {
    override fun describeToolCall(message: Message.Tool.Call): Message {
        return message
    }

    override fun describeToolResult(message: Message.Tool.Result): Message {
        return message
    }
}