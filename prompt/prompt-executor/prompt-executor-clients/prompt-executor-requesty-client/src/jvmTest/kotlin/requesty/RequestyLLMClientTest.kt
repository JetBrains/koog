package requesty

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.requesty.RequestyClientSettings
import ai.koog.prompt.executor.clients.requesty.RequestyLLMClient
import ai.koog.prompt.executor.clients.requesty.RequestyModels
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json as KotlinxJson

class RequestyLLMClientTest {

    object FixedClock : KoogClock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    val engine = MockEngine { error("No HTTP expected") }
    val http = HttpClient(engine) {}
    val key = "test-key"
    val content = "Hello from Requesty"

    //language=json
    val body = """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "created": 1716920000,
          "model": "openai/gpt-4o-mini",
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
          "system_fingerprint": "fp_dummy",
          "model": "openai/gpt-4o-mini",
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
          "model": "openai/gpt-4o-mini",
          "choices": [
            {"index": 0, "message": {"role": "assistant", "content": "{\"name\":\"Alice\"}"}, "finish_reason": "stop"}
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    //language=json
    val toolCallBody = """
        {
          "id": "chatcmpl-tool",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "anthropic/claude-sonnet-4-5",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": null,
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
    val modelsBody = """
        {
          "object": "list",
          "data": [
            {
              "id": "openai/gpt-4o-mini",
              "object": "model",
              "created": 1721172741,
              "owned_by": "openai",
              "api": "chat",
              "context_window": 128000,
              "max_output_tokens": 16384,
              "supports_tool_calling": true,
              "supports_vision": true
            },
            {
              "id": "some-lab/brand-new-model",
              "object": "model",
              "owned_by": "some-lab"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testExecute() = runTest {
        var capturedUrl = ""
        var capturedMethod: HttpMethod? = null
        var capturedAuth: String? = null
        var capturedBody: String? = null

        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            capturedMethod = req.method
            capturedAuth = req.headers[HttpHeaders.Authorization]
            capturedBody = (req.body as TextContent).text
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val settings = RequestyClientSettings()
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, settings = settings, clock = FixedClock)

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hello") }

        val responses = client.execute(prompt, RequestyModels.GPT4oMini)

        assertTrue(capturedUrl.startsWith("https://router.requesty.ai/"))
        assertTrue(capturedUrl.endsWith("v1/chat/completions"))
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("Bearer $key", capturedAuth)
        assertNotNull(capturedBody)
        val requestJson = KotlinxJson.parseToJsonElement(capturedBody).jsonObject
        assertEquals("openai/gpt-4o-mini", requestJson["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals(1, responses.parts.size)
        val textPart = assertIs<MessagePart.Text>(responses.parts.first())
        assertEquals(content, textPart.text)
    }

    @Test
    fun testExecuteWithCustomBaseUrl() = runTest {
        var capturedUrl = ""

        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val settings = RequestyClientSettings(baseUrl = "https://router.eu.requesty.ai")
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, settings = settings, clock = FixedClock)

        client.execute(Prompt.build(id = "p1", clock = FixedClock) { user("Hello") }, RequestyModels.GPT4oMini)

        assertEquals("https://router.eu.requesty.ai/v1/chat/completions", capturedUrl)
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
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val choices = client.executeMultipleChoices(prompt, RequestyModels.GPT4oMini, tools = emptyList())
        assertEquals(2, choices.size, "Response should have two choices")
        val firstChoice = assertIs<MessagePart.Text>(choices[0].parts.first())
        assertEquals(optionA, firstChoice.text, "$optionA should be first")
        val secondChoice = assertIs<MessagePart.Text>(choices[1].parts.first())
        assertEquals(optionB, secondChoice.text, "$optionB should be second")
    }

    @Test
    fun testExecuteStructuredOutput() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { req ->
            capturedBody = (req.body as TextContent).text
            respond(
                content = structuredBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val schema = LLMParams.Schema.JSON.Standard("Person", buildJsonObject { })

        val prompt = Prompt.build(
            id = "p-struct",
            clock = FixedClock,
            params = LLMParams(schema = schema)
        ) {
            user("Return a person info as a JSON")
        }

        val responses = client.execute(prompt, RequestyModels.GPT4oMini)
        assertEquals(1, responses.parts.size, "Response should have one choice")
        assertNotNull(capturedBody, "Captured body should not be null")
        assertTrue(capturedBody.contains("\"response_format\""), "Request body should contain response_format")
        assertTrue(capturedBody.contains("\"json_schema\""), "Request body should contain json_schema")
        val textPart = assertIs<MessagePart.Text>(responses.parts.first())
        assertTrue(textPart.text.contains("{\"name\":\"Alice\"}"))
    }

    @Test
    fun testExecuteToolCall() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = toolCallBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool", clock = FixedClock) { user("What is the weather in Boston?") }
        val responses = client.execute(prompt, RequestyModels.ClaudeSonnet4_5)

        assertEquals(1, responses.parts.size)
        val toolCallPart = assertIs<MessagePart.Tool.Call>(responses.parts.first())
        assertEquals("call_weather", toolCallPart.id)
        assertEquals("weather", toolCallPart.tool)
    }

    @Test
    fun testExecuteStreaming() = runTest {
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt.build(id = "p-stream", clock = FixedClock) { user("Stream it") }
        val flow = client.executeStreaming(prompt, RequestyModels.GPT4oMini)
        // MockEngine does not support Ktor SSE end-to-end streaming reliably in tests,
        // so only verify that the streaming flow can be created.
        assertNotNull(flow, "Flow should not be null")
    }

    @Test
    fun testStreamingChunksAreDecoded() = runTest {
        val reasoning = "Thinking about the answer."
        val answer = "Hello!"

        //language=json
        val reasoningChunk =
            """{"id":"c","object":"chat.completion.chunk","created":0,"model":"openai/gpt-5-mini","choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"$reasoning"}}]}"""
        //language=json
        val answerChunk =
            """{"id":"c","object":"chat.completion.chunk","created":0,"model":"openai/gpt-5-mini","choices":[{"index":0,"delta":{"content":"$answer"},"finish_reason":"stop"}]}"""
        //language=json
        val usageChunk =
            """{"id":"c","object":"chat.completion.chunk","created":0,"model":"openai/gpt-5-mini","choices":[],"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}"""

        val transport = object : KoogHttpClient {
            override val clientName: String = "RequestyStreamingTestClient"

            override suspend fun <R : Any> get(
                path: String,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("GET is not expected in this test")

            override suspend fun <T : Any, R : Any> post(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("POST is not expected in this test")

            override fun <T : Any, R : Any, O : Any> sse(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                dataFilter: (String?) -> Boolean,
                decodeStreamingResponse: (String) -> R,
                processStreamingChunk: (R) -> O?,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<O> = flow {
                for (data in listOf(reasoningChunk, answerChunk, usageChunk)) {
                    if (dataFilter(data)) {
                        processStreamingChunk(decodeStreamingResponse(data))?.let { emit(it) }
                    }
                }
            }

            override fun <T : Any> lines(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<String> = error("lines is not expected in this test")

            override fun close(): Unit = Unit
        }

        val client = RequestyLLMClient(httpClient = transport, clock = FixedClock)

        val frames = client.executeStreaming(
            Prompt.build(id = "p-stream", clock = FixedClock) { user("Say hello") },
            RequestyModels.GPT5Mini,
        ).toList()

        assertTrue(frames.any { it is StreamFrame.ReasoningDelta && it.text == reasoning })
        assertTrue(frames.any { it is StreamFrame.TextDelta && it.text == answer })
        val end = frames.filterIsInstance<StreamFrame.End>().single()
        assertEquals("stop", end.finishReason)
        assertEquals(7, end.metaInfo.totalTokensCount)
    }

    @Test
    fun testModels() = runTest {
        var capturedUrl = ""
        val engine = MockEngine { req ->
            capturedUrl = req.url.toString()
            respond(
                content = modelsBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val models = client.models()

        assertEquals("https://router.requesty.ai/v1/models", capturedUrl)
        assertEquals(2, models.size)
        assertEquals(RequestyModels.GPT4oMini, models[0], "Known models should resolve to the predefined definition")
        assertEquals("some-lab/brand-new-model", models[1].id)
        assertEquals(LLMProvider.Requesty, models[1].provider)
    }

    @Test
    fun testUnsupportedModeration() = runTest {
        val settings = RequestyClientSettings(
            baseUrl = "https://router.requesty.ai",
            chatCompletionsPath = "v1/chat/completions",
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 12345,
                connectTimeoutMillis = 2345,
                socketTimeoutMillis = 3456
            )
        )
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, settings = settings, clock = FixedClock)

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hi!") }
        val ex = assertFailsWith<UnsupportedOperationException> {
            client.moderate(prompt, RequestyModels.GPT4oMini)
        }
        assertTrue(ex.message!!.contains("Moderation is not supported"))
    }

    @Test
    fun testUnsupportedEmbedding() = runTest {
        val client = RequestyLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val ex = assertFailsWith<UnsupportedOperationException> {
            client.embed("text", RequestyModels.GPT4oMini)
        }
        assertTrue(ex.message!!.contains("Embedding is not supported"))
    }
}
