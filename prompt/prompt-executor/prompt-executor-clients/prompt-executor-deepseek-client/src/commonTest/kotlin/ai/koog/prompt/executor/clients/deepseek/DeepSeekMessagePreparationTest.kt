package ai.koog.prompt.executor.clients.deepseek

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.test.utils.CapturingKoogHttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class DeepSeekMessagePreparationTest {
    private val responseBody = """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "created": 1716920000,
          "system_fingerprint": "test-fingerprint",
          "model": "deepseek-chat",
          "choices": [
            {
              "index": 0,
              "message": {"role": "assistant", "content": "Done"},
              "finish_reason": "stop"
            }
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    @Test
    fun testReasoningMessageIsMergedWithFollowingToolCall() = runTest {
        val transport = CapturingKoogHttpClient("DeepSeekMessagePreparationTest") { responseType ->
            when (responseType) {
                String::class -> responseBody
                else -> error("Unexpected response type: $responseType")
            }
        }
        val client = DeepSeekLLMClient(httpClient = transport)
        val prompt = Prompt(
            id = "p-tool-history",
            messages = listOf(
                Message.User("What is the weather in Boston?", RequestMetaInfo.Empty),
                Message.Assistant(
                    parts = listOf(
                        MessagePart.Reasoning(content = listOf("I should call the weather tool."))
                    ),
                    metaInfo = ResponseMetaInfo.Empty
                ),
                Message.Assistant(
                    parts = listOf(
                        MessagePart.Tool.Call(
                            id = "call_weather",
                            tool = "weather",
                            args = JsonObject(mapOf("city" to JsonPrimitive("Boston")))
                        )
                    ),
                    metaInfo = ResponseMetaInfo.Empty
                ),
            )
        )

        client.execute(prompt, DeepSeekModels.DeepSeekV4Flash)

        val messages = Json.parseToJsonElement(transport.lastRequest as String)
            .jsonObject.getValue("messages").jsonArray
        assertEquals(2, messages.size)
        val assistantMessage = messages[1].jsonObject
        assertEquals(
            "I should call the weather tool.",
            assistantMessage.getValue("reasoning_content").jsonPrimitive.content
        )
        assertEquals(
            "weather",
            assistantMessage.getValue("tool_calls").jsonArray.single()
                .jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content
        )
    }
}
