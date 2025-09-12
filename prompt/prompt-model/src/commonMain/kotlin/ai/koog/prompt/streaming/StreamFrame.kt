package ai.koog.prompt.streaming

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Represents a frame of a streaming response from a LLM.
 */
@Serializable
public sealed interface StreamFrame {

    /**
     * Represents a frame of a streaming response from a LLM that appends some text.
     *
     * @property text The text to append to the response.
     */
    @Serializable
    public data class Append(
        val text: String?,
        val finishReason: String? = null,
    ) : StreamFrame

    /**
     * Represents a frame of a streaming response from a LLM that contains a tool call.
     *
     * @property id The ID of the tool call.
     * @property name The name of the tool call.
     * @property content The content of the tool call.
     */
    @Serializable
    public data class ToolCall(
        val id: String?,
        val index: Int = 0,
        val name: String?,
        val content: String?
    ) : StreamFrame {

        /**
         * Lazily parses the content of the tool call as a JSON object.
         */
        val contentJson: JsonObject by lazy {
            Json.parseToJsonElement(content ?: "").jsonObject
        }
    }
}
