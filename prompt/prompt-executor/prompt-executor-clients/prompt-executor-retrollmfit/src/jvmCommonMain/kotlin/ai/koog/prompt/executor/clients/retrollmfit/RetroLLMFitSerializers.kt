package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Converts a [ai.koog.prompt.dsl.Prompt]'s messages into the JSON array injected by [@MessagesField][MessagesField].
 *
 * Implement this interface to support any API-specific message format.
 * The framework ships with [OpenAIChatSerializer]; you can provide your own for any other format
 * (Gemini `contents[].parts[].text`, Anthropic `messages[]`, custom internal formats, etc.)
 * without changing the framework.
 *
 * ### Example — custom Gemini format:
 * ```kotlin
 * object GeminiMessagesSerializer : MessagesSerializer {
 *     override fun serialize(messages: List<Message>): List<JsonObject> =
 *         messages.filter { it !is Message.System }.map { msg ->
 *             buildJsonObject {
 *                 put("role", if (msg is Message.Assistant) "model" else "user")
 *                 put("parts", buildJsonArray { add(buildJsonObject { put("text", (msg as? Message.User)?.content ?: "") }) })
 *             }
 *         }
 * }
 *
 * @MessagesField(GeminiMessagesSerializer::class)
 * val contents: List<JsonObject> = emptyList()
 * ```
 */
public interface MessagesSerializer {
    public fun serialize(messages: List<Message>): List<JsonObject>
}

/**
 * Serializes messages in OpenAI chat format:
 * `[{"role":"user","content":"…"}, {"role":"assistant","content":"…"}, …]`
 *
 * Compatible with OpenAI, Anthropic `/v1/messages`, OpenRouter, and any OpenAI-compatible endpoint.
 * This is the default for [@MessagesField][MessagesField].
 */
public object OpenAIChatSerializer : MessagesSerializer {
    override fun serialize(messages: List<Message>): List<JsonObject> =
        messages.flatMap { it.toOpenAIJson() }

    internal fun Message.toOpenAIJson(): List<JsonObject> = when (this) {
        is Message.System    -> listOf(buildJsonObject { put("role", "system");    put("content", content) })
        is Message.User      -> listOf(buildJsonObject { put("role", "user");      put("content", content) })
        is Message.Assistant -> listOf(buildJsonObject { put("role", "assistant"); put("content", content) })
        is Message.Tool.Result -> listOf(buildJsonObject {
            put("role", "tool")
            id?.let { put("tool_call_id", it) }
            put("content", content)
        })
        is Message.Tool.Call -> listOf(buildJsonObject {
            put("role", "assistant")
            put("tool_calls", buildJsonArray {
                add(buildJsonObject {
                    id?.let { put("id", it) }
                    put("type", "function")
                    put("function", buildJsonObject { put("name", tool); put("arguments", content) })
                })
            })
        })
        else -> emptyList()
    }
}
