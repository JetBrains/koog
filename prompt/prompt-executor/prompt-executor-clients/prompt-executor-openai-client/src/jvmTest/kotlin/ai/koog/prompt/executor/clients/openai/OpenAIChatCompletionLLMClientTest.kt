package ai.koog.prompt.executor.clients.openai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    // ---------------------------------------------------------------------------------------------
    // Gemini 3 thought_signature round-trip (Vertex OpenAI-compat). Gemini 3 returns the signature
    // on each function call in tool_calls[].extra_content.google.thought_signature and rejects the
    // next tool turn with HTTP 400 if it is not echoed back verbatim.
    // ---------------------------------------------------------------------------------------------

    private val toolCallWithThoughtSignatureBody = """
        {
          "id": "chatcmpl-g3",
          "object": "chat.completion",
          "created": 1677652288,
          "model": "gemini-3.5-flash",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "tool_calls": [
                  {
                    "id": "call_g3",
                    "type": "function",
                    "extra_content": { "google": { "thought_signature": "SIG_ABC_123" } },
                    "function": {
                      "name": "weather",
                      "arguments": "{\"city\": \"Sofia\"}"
                    }
                  }
                ]
              },
              "finish_reason": "tool_calls"
            }
          ]
        }
    """.trimIndent()

    private val weatherTools = listOf(
        ToolDescriptor(
            name = "weather",
            description = "Get the weather for a city",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "city",
                    description = "The city to get weather for",
                    type = ToolParameterType.String,
                ),
            ),
        ),
    )

    @Test
    fun testGemini3ThoughtSignatureParsedIntoReasoningPart() = runTest {
        // INBOUND: extra_content.google.thought_signature must be lifted into a signature-only
        // Reasoning part placed immediately before its tool call.
        val engine = MockEngine {
            respond(
                content = toolCallWithThoughtSignatureBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OpenAILLMClient(
            apiKey = key,
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(engine)),
            clock = FixedClock,
        )

        val response = client.execute(
            Prompt.build("g3-in") { user("What's the weather in Sofia?") },
            OpenAIModels.Chat.GPT4o,
            weatherTools,
        )

        val reasoningIndex = response.parts.indexOfFirst {
            it is MessagePart.Reasoning && it.encrypted == "SIG_ABC_123"
        }
        val callIndex = response.parts.indexOfFirst { it is MessagePart.Tool.Call }
        assertNotEquals(-1, reasoningIndex, "thought_signature was not lifted into a Reasoning part")
        assertEquals(reasoningIndex + 1, callIndex, "Reasoning(signature) must directly precede its tool call")
    }

    @Test
    fun testGemini3ThoughtSignatureEchoedBackOnNextTurn() = runTest {
        // ROUND-TRIP: feed the assistant tool call from turn 1 back on turn 2 and assert the
        // request carries tool_calls[0].extra_content.google.thought_signature verbatim.
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = toolCallWithThoughtSignatureBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OpenAILLMClient(
            apiKey = key,
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(engine)),
            clock = FixedClock,
        )

        // Turn 1 — obtain the assistant message carrying the signature.
        val assistantTurn = client.execute(
            Prompt.build("g3-turn1") { user("What's the weather in Sofia?") },
            OpenAIModels.Chat.GPT4o,
            weatherTools,
        )

        // Turn 2 — replay the assistant tool call + a tool result, capture the outbound request.
        val followUp = Prompt.build("g3-turn2") {
            user("What's the weather in Sofia?")
            message(assistantTurn)
            message(
                Message.User(
                    MessagePart.Tool.Result(id = "call_g3", tool = "weather", output = "sunny"),
                    RequestMetaInfo.create(FixedClock),
                ),
            )
        }
        client.execute(followUp, OpenAIModels.Chat.GPT4o, weatherTools)

        val body = Json.parseToJsonElement(requireNotNull(capturedBody)).jsonObject
        val toolCall = body["messages"]!!.jsonArray
            .first { it.jsonObject["tool_calls"] != null }
            .jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        val signature = toolCall["extra_content"]
            ?.jsonObject?.get("google")
            ?.jsonObject?.get("thought_signature")
            ?.jsonPrimitive?.content
        assertEquals("SIG_ABC_123", signature, "thought_signature was not echoed back: $toolCall")
    }

    @Test
    fun testNonGeminiToolCallHasNoExtraContent() = runTest {
        // NO-REGRESS: a plain tool call (no extra_content inbound) must NOT add extra_content
        // outbound, so OpenAI / OpenRouter / Gemini 2.5 requests stay byte-for-byte unchanged.
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = toolCallWithReasoningBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = OpenAILLMClient(
            apiKey = key,
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(engine)),
            clock = FixedClock,
        )

        val assistantTurn = client.execute(
            Prompt.build("plain-turn1") { user("What's the weather in Boston?") },
            OpenAIModels.Chat.GPT4o,
            weatherTools,
        )

        val followUp = Prompt.build("plain-turn2") {
            user("What's the weather in Boston?")
            message(assistantTurn)
            message(
                Message.User(
                    MessagePart.Tool.Result(id = "call_abc123", tool = "weather", output = "rainy"),
                    RequestMetaInfo.create(FixedClock),
                ),
            )
        }
        client.execute(followUp, OpenAIModels.Chat.GPT4o, weatherTools)

        val body = Json.parseToJsonElement(requireNotNull(capturedBody)).jsonObject
        val toolCall = body["messages"]!!.jsonArray
            .first { it.jsonObject["tool_calls"] != null }
            .jsonObject["tool_calls"]!!.jsonArray[0].jsonObject
        assertNull(toolCall["extra_content"], "non-Gemini tool call must not carry extra_content")
    }
}
