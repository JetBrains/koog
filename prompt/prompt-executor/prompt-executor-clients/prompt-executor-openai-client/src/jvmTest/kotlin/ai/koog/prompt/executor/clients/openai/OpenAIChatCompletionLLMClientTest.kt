package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Instant

class OpenAIChatCompletionLLMClientTest {

    object FixedClock : KoogClock {
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

    //language=json
    private val plainResponseBody = """
        {
          "id": "chatcmpl-plain",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "message": {"role": "assistant", "content": "The weather in Boston is 72F."},
              "finish_reason": "stop"
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
        val client = OpenAILLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool-response", clock = FixedClock, params = OpenAIChatParams()) {
            user("What is the weather in Boston?")
        }

        val responses = client.execute(prompt, OpenAIModels.Chat.GPT4o)

        assertEquals(2, responses.parts.size, "Response should contain reasoning and tool call")
        val reasoningPart = assertIs<MessagePart.Reasoning>(responses.parts[0])
        assertEquals(1, reasoningPart.content.size, "Reasoning should contain one message")
        assertEquals("I should call the weather tool first.", reasoningPart.content.first())

        val toolCall = assertIs<MessagePart.Tool.Call>(responses.parts[1])
        assertEquals("call_weather", toolCall.id)
        assertEquals("weather", toolCall.tool)
        assertEquals(buildJsonObject { put("city", JsonPrimitive("Boston")) }, toolCall.argsJson)
    }

    @Test
    fun testToolCallArgumentsAreNotDoubleEncodedInRequest() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { req ->
            capturedBody = (req.body as TextContent).text
            respond(
                content = plainResponseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = OpenAILLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt(
            id = "p-toolcall-args",
            messages = listOf(
                Message.User("What is the weather in Boston?", RequestMetaInfo.Empty),
                Message.Assistant(
                    parts = listOf(
                        MessagePart.Tool.Call(
                            id = "call_weather",
                            tool = "weather",
                            args = JsonObject(mapOf("city" to JsonPrimitive("Boston"))),
                        ),
                    ),
                    metaInfo = ResponseMetaInfo.Empty
                ),
                Message.User(
                    parts = listOf(
                        MessagePart.Tool.Result(
                            id = "call_weather",
                            tool = "weather",
                            output = "{\"temperature\":72}",
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            )
        )

        client.execute(prompt, OpenAIModels.Chat.GPT4o)

        assertNotNull(capturedBody, "Captured request body should not be null")
        val messages = Json.parseToJsonElement(capturedBody).jsonObject["messages"]!!.jsonArray
        val assistantMessage = messages
            .first { it.jsonObject["role"]?.jsonPrimitive?.contentOrNull == "assistant" }
            .jsonObject
        val arguments = assistantMessage["tool_calls"]!!.jsonArray[0]
            .jsonObject["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content

        // MessagePart.Tool.Call.args already holds JSON-encoded arguments. The serializer must emit
        // it verbatim; re-encoding produced a double-encoded (quoted) string that strict
        // OpenAI-compatible backends (e.g. DashScope) reject. arguments must decode to the object.
        val decoded = Json.parseToJsonElement(arguments)
        assertIs<JsonObject>(decoded, "function.arguments must be a JSON object, not a double-encoded JSON string")
        assertEquals(JsonObject(mapOf("city" to JsonPrimitive("Boston"))), decoded)
    }
}
