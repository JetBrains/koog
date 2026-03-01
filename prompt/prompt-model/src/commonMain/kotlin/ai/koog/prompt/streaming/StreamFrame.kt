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
     * The interface representing a complete frame of a streaming response from a LLM.
     */
    public sealed interface CompleteFrame : StreamFrame {
        public val index: Int?
    }

    /**
     * The interface representing a delta or partial frame of a streaming response from a LLM.
     */
    public sealed interface DeltaFrame : StreamFrame {
        public val index: Int?
    }

    /**
     * Represents a frame of a streaming response from a LLM with text delta.
     *
     * @property text The text to append to the response.
     */
    @Serializable
    public data class TextDelta(
        val text: String,
        override val index: Int? = null
    ) : DeltaFrame, StreamFrame

    /**
     * Represents a completion of a streaming response text part.
     *
     * @property text The complete text of the response.
     */
    @Serializable
    public data class TextComplete(
        val text: String,
        override val index: Int? = null
    ) : CompleteFrame, StreamFrame

    /**
     * Represents a frame of a streaming response from a LLM with reasoning text delta.
     *
     * @property text The text to append to the reasoning text.
     */
    @Serializable
    public data class ReasoningDelta(
        val text: String? = null,
        val summary: String? = null,
        override val index: Int? = null
    ) : DeltaFrame, StreamFrame

    /**
     * Represents a frame of a streaming response from a LLM with reasoning text delta.
     *
     * @property text The text to append to the reasoning text.
     */
    @Serializable
    public data class ReasoningComplete(
        val text: List<String>,
        val summary: List<String>? = null,
        public val encrypted: String? = null,
        override val index: Int? = null
    ) : CompleteFrame, StreamFrame

    /**
     * Represents a frame of a streaming response from a LLM that contains a tool call.
     *
     * @property id The ID of the tool call. Can be null for partial frames.
     * @property name The name of the tool being called. Can be null for partial frames.
     * @property content The content/arguments of the tool call. Can be null for partial frames.
     */
    @Serializable
    public data class ToolCallDelta(
        val id: String?,
        val name: String?,
        val content: String?,
        override val index: Int? = null
    ) : DeltaFrame, StreamFrame

    /**
     * Represents a frame of a streaming response from a LLM that contains a tool call.
     *
     * @property id The ID of the tool call. Can be null for partial frames.
     * @property name The name of the tool being called. Can be null for partial frames.
     * @property content The complete content/arguments of the tool call..
     */
    @Serializable
    public data class ToolCallComplete(
        val id: String?,
        val name: String,
        val content: String,
        override val index: Int? = null
    ) : CompleteFrame, StreamFrame {

        /**
         * Lazily parses and caches the result of parsing [content] as a JSON object.
         */
        val contentJsonResult: Result<JsonObject> by lazy {
            runCatching { Json.parseToJsonElement(content).jsonObject }
        }

        /**
         * Lazily parses the content of the tool call as a JSON object.
         * Can throw an exception when parsing fails.
         */
        val contentJson: JsonObject
            get() = contentJsonResult.getOrThrow()
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
