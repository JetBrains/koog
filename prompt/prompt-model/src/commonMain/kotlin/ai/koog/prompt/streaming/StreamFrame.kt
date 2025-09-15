package ai.koog.prompt.streaming

import ai.koog.prompt.message.ResponseMetaInfo
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
        val text: String
    ) : StreamFrame

    /**
     * Represents a frame of a streaming response from a LLM that contains a tool call.
     *
     * @property id The ID of the tool call. Can be null for partial frames.
     * @property name The name of the tool being called. Can be null for partial frames.
     * @property content The content/arguments of the tool call. Can be null for partial frames.
     */
    @Serializable
    public data class ToolCall(
        val id: String?,
        val name: String,
        val content: String
    ) : StreamFrame {

        /**
         * Lazily parses the content of the tool call as a JSON object.
         */
        val contentJson: JsonObject by lazy {
            Json.parseToJsonElement(content).jsonObject
        }
    }

    /**
     * Represents a frame of a streaming response from a LLM that signals the end of the stream.
     *
     * @property finishReason The reason for the stream to end.
     */
    @Serializable
    public data class End(
        val finishReason: String? = null,
        val metaInfo: ResponseMetaInfo = ResponseMetaInfo.Empty
    ) : StreamFrame
}
