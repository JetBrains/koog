package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

class OpenAIChatCompletionLLMClientTest {

    object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val key = "test-key"

    //language=json
    private val toolCallWithReasoningBody = """
        {
          "id": "chatcmpl-tool",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "",
                "reasoning_content": "I should call the weather tool first.",
                "tool_calls": [
                  {
                    "id": "call_weather",
                    "type": "function",
                    "function": {
                      "name": "weather",
                      "arguments": "{\"city\":\"Boston\"}"
                    }
                  }
                ]
              },
              "finish_reason": "tool_calls"
            }
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    @Test
    fun testExecuteToolCallResponsePreservesReasoningMessage() = runTest {
        val engine = MockEngine.Companion { _ ->
            respond(
                content = toolCallWithReasoningBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenAILLMClient(apiKey = key, baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool-response", clock = FixedClock, params = OpenAIChatParams()) {
            user("What is the weather in Boston?")
        }

        val responses = client.execute(prompt, OpenAIModels.Chat.GPT4o)

        assertEquals(2, responses.size, "Response should contain reasoning and tool call")
        assertIs<Message.Reasoning>(responses[0])
        assertEquals("I should call the weather tool first.", responses[0].content)
        val toolCall = assertIs<Message.Tool.Call>(responses[1])
        assertEquals("call_weather", toolCall.id)
        assertEquals("weather", toolCall.tool)
        assertEquals("{\"city\":\"Boston\"}", toolCall.content)
    }
}
