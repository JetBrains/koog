package ai.koog.prompt.streaming

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
public sealed interface StreamingFrame {

    @Serializable
    public data class Append(
        val text: String
    ) : StreamingFrame

    @Serializable
    public data class ToolCall(
        val id: String?,
        val name: String,
        val content: String
    ) : StreamingFrame {

        /**
         * Lazily parses the content of the tool call as a JSON object.
         */
        val contentJson: JsonObject by lazy {
            Json.parseToJsonElement(content).jsonObject
        }
    }
}
