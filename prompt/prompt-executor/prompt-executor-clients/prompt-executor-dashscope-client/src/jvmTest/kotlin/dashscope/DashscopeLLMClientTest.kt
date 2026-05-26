package dashscope

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.dashscope.DashscopeClientSettings
import ai.koog.prompt.executor.clients.dashscope.DashscopeLLMClient
import ai.koog.prompt.executor.clients.dashscope.DashscopeModels
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.utils.time.KoogClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DashscopeLLMClientTest {

    object FixedClock : KoogClock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    val engine = MockEngine { error("No HTTP expected") }
    val http = HttpClient(engine) {}
    val key = "test-key"
    val content = "Hello from DashScope"

    //language=json
    val body = """
        {
        "id": "chatcmpl-123",
        "object": "chat.completion",
        "created": 1716920000,
        "system_fingerprint": "dummy",
        "model": "qwen-plus",
        "choices": [
            {
        "index": 0,
        "message": {"role": "assistant", "content": "$content"},
        "finish_reason": "stop"
            }
        ],
        "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    val optionA = "Choice A"
    val optionB = "Choice B"

    //language=json
    val bodyMultipleChoices = """
        {
          "id": "chatcmpl-456",
          "object": "chat.completion",
          "created": 1716920003,
          "system_fingerprint": "dummy",
          "model": "qwen-plus",
          "choices": [
            {
              "index": 0,
              "message": {"role": "assistant", "content": "$optionA"},
              "finish_reason": "stop"
            },
            {
              "index": 1,
              "message": {"role": "assistant", "content": "$optionB"},
              "finish_reason": "stop"
            }
          ],
          "usage": {"total_tokens": 20, "prompt_tokens": 10, "completion_tokens": 10}
        }
    """.trimIndent()

    //language=json
    val structuredBody = """
        {
          "id": "chatcmpl-789",
          "object": "chat.completion",
          "created": 1716920004,
          "system_fingerprint": "dummy",
          "model": "qwen-max",
          "choices": [
            {"index": 0, "message": {"role": "assistant", "content": "{\"name\":\"Alice\"}"}, "finish_reason": "stop"}
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    //language=json
    val complexUsageBody = """
        {
          "id": "chatcmpl-789",
          "object": "chat.completion",
          "created": 1716920004,
          "system_fingerprint": "dummy",
          "model": "qwen-long",
          "choices": [
            {"index": 0, "message": {"role": "assistant", "content": "{\"name\":\"Alice\"}"}, "finish_reason": "stop"}
          ],
          "usage" : {
              "prompt_tokens" : 35,
              "completion_tokens" : 191,
              "total_tokens" : 226,
              "prompt_tokens_details" : {
                "cached_tokens" : 0
              },
              "completion_tokens_details" : {
                "reasoning_tokens" : 100
              },
              "prompt_cache_hit_tokens" : 0,
              "prompt_cache_miss_tokens" : 35
          }
        }
    """.trimIndent()

    //language=json
    val toolCallWithReasoningBody = """
        {
          "id": "chatcmpl-tool",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "qwen-plus",
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
    fun testExecute() = runTest {
        var capturedUrl = ""
        var capturedMethod: HttpMethod? = null
        var capturedAuth: String? = null

        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            capturedMethod = req.method
            capturedAuth = req.headers[HttpHeaders.Authorization]
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val settings = DashscopeClientSettings()
        val client = DashscopeLLMClient(
            httpClientFactory = KtorKoogHttpClient.Factory(http),
            apiKey = key,
            settings = settings,
            clock = FixedClock
        )

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hello") }

        val responses = client.execute(prompt, DashscopeModels.QWEN_FLASH)

        assertTrue(capturedUrl.startsWith("https://dashscope-intl.aliyuncs.com/"))
        assertTrue(capturedUrl.endsWith("compatible-mode/v1/chat/completions"))
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("Bearer $key", capturedAuth)
        assertEquals(1, responses.parts.size)
        val textPart = assertIs<MessagePart.Text>(responses.parts.first())
        assertEquals(content, textPart.text)
    }

    @Test
    fun testExecuteMultipleChoices() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = bodyMultipleChoices,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client =
            DashscopeLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val choices = client.executeMultipleChoices(prompt, DashscopeModels.QWEN_PLUS, tools = emptyList())
        assertEquals(2, choices.size, "Response should have two choices")

        val fistTextPart = assertIs<MessagePart.Text>(choices[0].parts.first())
        assertEquals(optionA, fistTextPart.text, "$optionA should be first")

        val secondTextPart = assertIs<MessagePart.Text>(choices[1].parts.first())
        assertEquals(optionB, secondTextPart.text, "$optionB should be second")
    }

    @Test
    fun testExecuteStructuredOutput() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { req ->
            val content = req.body as TextContent
            capturedBody = content.text

            respond(
                content = structuredBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client =
            DashscopeLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val schemaJson = buildJsonObject {
            put(
                "type",
                "object"
            )
            putJsonObject("properties") { putJsonObject("name") { put("type", "string") } }
        }

        val schema = LLMParams.Schema.JSON.Basic("Person", schemaJson)

        val prompt = Prompt.build(
            id = "p-struct",
            clock = FixedClock,
            params = LLMParams(schema = schema)
        ) {
            user("Return a person info as a JSON")
        }

        val responses = client.execute(prompt, DashscopeModels.QWEN_PLUS)
        assertEquals(1, responses.parts.size, "Response should have one choice")
        assertNotNull(capturedBody, "Captured body should not be null")
        assertTrue(capturedBody.contains("\"response_format\""), "Response body should contain response_format")
        assertTrue(capturedBody.contains("\"json_schema\""), "Response body should contain json_schema")
        val textPart = assertIs<MessagePart.Text>(responses.parts.first())
        assertEquals("{\"name\":\"Alice\"}", textPart.text)
    }

    @Test
    fun testExecuteStreaming() = runTest {
        val client = DashscopeLLMClient(
            httpClientFactory = KtorKoogHttpClient.Factory(http),
            apiKey = "test-key",
            clock = FixedClock
        )

        val prompt = Prompt.build(id = "p-stream", clock = FixedClock) { user("Stream it") }
        val flow = client.executeStreaming(prompt, DashscopeModels.QWEN_FLASH)
        // For now, we'd only verify that streaming flow can be created
        // as MockEngine does not support Ktor SSE end-to-end streaming reliably in tests
        assertNotNull(flow, "Flow should not be null")
    }

    @Test
    fun testExecuteToolCallResponsePreservesReasoningMessage() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = toolCallWithReasoningBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client =
            DashscopeLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool-response", clock = FixedClock) {
            user("What is the weather in Boston?")
        }

        val responses = client.execute(prompt, DashscopeModels.QWEN_PLUS)

        assertEquals(2, responses.parts.size, "Response should contain reasoning and tool call")
        val reasoning = assertIs<MessagePart.Reasoning>(responses.parts[0])
        assertEquals(1, reasoning.content.size)
        assertEquals("I should call the weather tool first.", reasoning.content.first())

        val toolCall = assertIs<MessagePart.Tool.Call>(responses.parts[1])
        assertEquals("call_weather", toolCall.id)
        assertEquals("weather", toolCall.tool)
        assertEquals(
            buildJsonObject { put("city", JsonPrimitive("Boston")) },
            toolCall.argsJson
        )
    }

    @Test
    fun testUnsupportedModeration() = runTest {
        val settings = DashscopeClientSettings(
            baseUrl = "https://dashscope.aliyuncs.com/",
            chatCompletionsPath = "compatible-mode/v1/chat/completions",
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 12345,
                connectTimeoutMillis = 2345,
                socketTimeoutMillis = 3456
            )
        )
        val client = DashscopeLLMClient(
            httpClientFactory = KtorKoogHttpClient.Factory(http),
            apiKey = key,
            settings = settings,
            clock = FixedClock
        )

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hi!") }
        val ex = assertFailsWith<UnsupportedOperationException> {
            client.moderate(prompt, DashscopeModels.QWEN_FLASH)
        }
        assertTrue(ex.message!!.contains("Moderation is not supported"))
    }

    @Test
    fun testResponseUsage() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = complexUsageBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client =
            DashscopeLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val response = client.execute(prompt, DashscopeModels.QWEN_PLUS, tools = emptyList())
        assertEquals(1, response.parts.size, "Response should have once response")
        assertEquals(35, response.metaInfo.inputTokensCount)
        assertEquals(191, response.metaInfo.outputTokensCount)
        assertEquals(226, response.metaInfo.totalTokensCount)
    }
}
