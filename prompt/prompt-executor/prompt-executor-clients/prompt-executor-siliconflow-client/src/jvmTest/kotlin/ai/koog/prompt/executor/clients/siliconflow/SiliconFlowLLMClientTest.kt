package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

class SiliconFlowLLMClientTest {

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val apiKey = "test-api-key"

    //language=json
    private val toolCallWithReasoningBody = """
        {
          "id": "chatcmpl-tool",
          "created": 1716920005,
          "model": "Pro/deepseek-ai/DeepSeek-R1",
          "object": "chat.completion",
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

    //language=json
    private val topLevelErrorBody = """
        {
          "id": "chatcmpl-error",
          "created": 1716920005,
          "model": "Pro/deepseek-ai/DeepSeek-R1",
          "object": "chat.completion",
          "choices": [],
          "error": {
            "message": "Invalid API key",
            "type": "invalid_request_error",
            "code": "401"
          }
        }
    """.trimIndent()

    //language=json
    private val emptyChoicesBody = """
        {
          "id": "chatcmpl-empty",
          "created": 1716920005,
          "model": "Pro/deepseek-ai/DeepSeek-R1",
          "object": "chat.completion",
          "choices": []
        }
    """.trimIndent()

    @Test
    fun testExecuteToolCallResponsePreservesReasoningMessage() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = toolCallWithReasoningBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool-response", clock = FixedClock) {
            user("What is the weather in Boston?")
        }

        val responses = client.execute(prompt, SiliconFlowModels.ProDeepSeekR1)

        assertEquals(2, responses.size)
        assertIs<Message.Reasoning>(responses[0])
        assertEquals("I should call the weather tool first.", responses[0].content)

        val toolCall = assertIs<Message.Tool.Call>(responses[1])
        assertEquals("call_weather", toolCall.id)
        assertEquals("weather", toolCall.tool)
        assertEquals("{\"city\":\"Boston\"}", toolCall.content)
    }

    @Test
    fun testExecuteThrowsOnTopLevelErrorResponse() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = topLevelErrorBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p-top-level-error", clock = FixedClock) {
            user("Hi")
        }

        val exception = assertFailsWith<LLMClientException> {
            client.execute(prompt, SiliconFlowModels.ProDeepSeekR1)
        }
        assertEquals(true, exception.message?.contains("Invalid API key"))
    }

    @Test
    fun testExecuteThrowsOnEmptyChoicesWithoutError() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = emptyChoicesBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = SiliconFlowLLMClient(apiKey = apiKey, baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p-empty-choices", clock = FixedClock) {
            user("Hi")
        }

        assertFailsWith<IllegalArgumentException> {
            client.execute(prompt, SiliconFlowModels.ProDeepSeekR1)
        }
    }
}
