package deepseek

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.serialization.json.Json as KotlinxJson

class DeepSeekLLMClientTest {

    object FixedClock : KoogClock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    val engine = MockEngine { error("No HTTP expected") }
    val http = HttpClient(engine) {}
    val key = "test-key"
    val content = "Hello from DeepSeek"

    //language=json
    val body = """
        {
        "id": "chatcmpl-123",
        "object": "chat.completion", 
        "created": 1716920000,
        "system_fingerprint": "dummy",
        "model": "deepseek-chat",
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
          "model": "deepseek-chat",
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
          "model": "deepseek-chat",
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
          "model": "deepseek-chat",
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
          "system_fingerprint": "dummy",
          "model": "deepseek-reasoner",
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
        val settings = DeepSeekClientSettings()
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, settings = settings, clock = FixedClock)

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hello") }

        val responses = client.execute(prompt, DeepSeekModels.DeepSeekV4Flash)

        assertTrue(capturedUrl.startsWith("https://api.deepseek.com/"))
        assertTrue(capturedUrl.endsWith("chat/completions"))
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
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val choices = client.executeMultipleChoices(prompt, DeepSeekModels.DeepSeekV4Flash, tools = emptyList())
        assertEquals(2, choices.size, "Response should have two choices")
        assertEquals(1, choices[0].parts.size, "First choice should have one part")
        val firstChoice = assertIs<MessagePart.Text>(choices[0].parts.first())
        assertEquals(optionA, firstChoice.text, "$optionA should be first")

        assertEquals(1, choices[1].parts.size, "Second choice should have one part")
        val secondChoice = assertIs<MessagePart.Text>(choices[1].parts.first())
        assertEquals(optionB, secondChoice.text, "$optionB should be second")
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
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val schemaJson = buildJsonObject { }

        val schema = LLMParams.Schema.JSON.Basic("Person", schemaJson)

        val prompt = Prompt.build(
            id = "p-struct",
            clock = FixedClock,
            params = LLMParams(schema = schema)
        ) {
            user("Return a person info as a JSON")
        }

        val responses = client.execute(prompt, DeepSeekModels.DeepSeekV4Flash)
        assertEquals(1, responses.parts.size, "Response should have one choice")
        assertNotNull(capturedBody, "Captured body should not be null")
        assertTrue(capturedBody.contains("\"response_format\""), "Response body should contain response_format")
        assertTrue(capturedBody.contains("\"json_object\""), "Response body should contain json_schema")
        val textPart = assertIs<MessagePart.Text>(responses.parts.first())
        assertTrue(
            textPart.text.contains("{\"name\":\"Alice\"}"),
            "Response should contain JSON string [{\"name\":\"Alice\"}]"
        )
    }

    @Test
    fun testExecuteStreaming() = runTest {
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = "test-key", clock = FixedClock)

        val prompt = Prompt.build(id = "p-stream", clock = FixedClock) { user("Stream it") }
        val flow = client.executeStreaming(prompt, DeepSeekModels.DeepSeekV4Flash)
        // For now, we'd only verify that streaming flow can be created
        // as MockEngine does not support Ktor SSE end-to-end streaming reliably in tests
        assertNotNull(flow, "Flow should not be null")
    }

    @Test
    fun testStreamingReasoningIsEmittedAndReplayedToProvider() = runTest {
        val reasoning = "I should call the weather tool first."
        val answer = "The weather in Boston is 72F."

        // The reasoning-capable model streams reasoning under `reasoning_content` in one delta, then the answer.
        //language=json
        val reasoningChunk =
            """{"id":"c","object":"chat.completion.chunk","created":0,"system_fingerprint":"fp","model":"deepseek-reasoner","choices":[{"index":0,"delta":{"role":"assistant","reasoning_content":"$reasoning"}}]}"""
        //language=json
        val answerChunk =
            """{"id":"c","object":"chat.completion.chunk","created":0,"system_fingerprint":"fp","model":"deepseek-reasoner","choices":[{"index":0,"delta":{"content":"$answer"},"finish_reason":"stop"}]}"""

        var capturedBody: String? = null

        // A fake transport that drives the real decode + processStreamingResponse path for `sse`, and captures
        // the serialized request body for the follow-up `post`. MockEngine cannot drive Ktor SSE reliably in tests.
        val transport = object : KoogHttpClient {
            override val clientName: String = "DeepSeekStreamingTestClient"

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
            ): R {
                capturedBody = requestBody as String
                @Suppress("UNCHECKED_CAST")
                return body as R
            }

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
                for (data in listOf(reasoningChunk, answerChunk)) {
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

        val client = DeepSeekLLMClient(httpClient = transport, clock = FixedClock)

        // 1. The reasoning delta is emitted while streaming.
        val frames = client.executeStreaming(
            Prompt.build(id = "p-stream", clock = FixedClock) { user("What's the weather in Boston?") },
            DeepSeekModels.DeepSeekV4Flash,
        ).toList()

        assertTrue(
            frames.any { it is StreamFrame.ReasoningDelta && it.text == reasoning },
            "A reasoning delta should be emitted from the stream"
        )
        val reasoningComplete = frames.filterIsInstance<StreamFrame.ReasoningComplete>().single()
        assertEquals(listOf(reasoning), reasoningComplete.content)

        // 2. The streamed reasoning is preserved on the assistant message and replayed back to the provider.
        val assistant = frames.toMessageResponse()
        val reasoningPart = assistant.parts.filterIsInstance<MessagePart.Reasoning>().single()
        assertEquals(listOf(reasoning), reasoningPart.content)

        val followUp = Prompt(
            id = "p-followup",
            messages = listOf(
                Message.User("What's the weather in Boston?", RequestMetaInfo.Empty),
                assistant,
            )
        )
        client.execute(followUp, DeepSeekModels.DeepSeekV4Flash)

        assertNotNull(capturedBody, "The follow-up request body should be captured")
        val messages = KotlinxJson.parseToJsonElement(capturedBody).jsonObject["messages"]!!.jsonArray
        val assistantMessage = messages
            .first { it.jsonObject["role"]?.jsonPrimitive?.contentOrNull == "assistant" }
            .jsonObject
        assertEquals(
            reasoning,
            assistantMessage["reasoning_content"]?.jsonPrimitive?.contentOrNull,
            "Reasoning content received while streaming must be sent back to the provider"
        )
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
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt.build(id = "p-tool-response", clock = FixedClock) {
            user("What is the weather in Boston?")
        }

        val responses = client.execute(prompt, DeepSeekModels.DeepSeekV4Flash)

        assertEquals(2, responses.parts.size, "Response should contain reasoning and tool call")

        val reasoningPart = assertIs<MessagePart.Reasoning>(responses.parts[0])
        assertEquals(1, reasoningPart.content.size, "Reasoning should contain one message")
        assertEquals("I should call the weather tool first.", reasoningPart.content.first())

        val toolCallPart = assertIs<MessagePart.Tool.Call>(responses.parts[1])
        assertEquals("call_weather", toolCallPart.id)
        assertEquals("weather", toolCallPart.tool)
        assertEquals(buildJsonObject { put("city", JsonPrimitive("Boston")) }, toolCallPart.argsJson)
    }

    @Test
    fun testExecuteDeepSeekReasonerReplaysReasoningWithToolCalls() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { req ->
            capturedBody = (req.body as TextContent).text
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, Json.toString())
            )
        }
        val http = HttpClient(engine) {}
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)

        val prompt = Prompt(
            id = "p-tool-history",
            messages = listOf(
                Message.User("What's the weather in Boston?", RequestMetaInfo.Empty),
                Message.Assistant(
                    parts = listOf(
                        MessagePart.Reasoning(
                            content = listOf("I should call the weather tool first."),
                        ),
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
        client.execute(prompt, DeepSeekModels.DeepSeekV4Flash)

        assertNotNull(capturedBody, "Captured request body should not be null")
        val messages = KotlinxJson.parseToJsonElement(capturedBody).jsonObject["messages"]!!.jsonArray

        assertEquals(3, messages.size, "Reasoning and tool calls should be merged into a single assistant message")
        val assistantMessage = messages[1].jsonObject
        assertEquals("assistant", assistantMessage["role"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "I should call the weather tool first.",
            assistantMessage["reasoning_content"]?.jsonPrimitive?.contentOrNull
        )
        assertNull(assistantMessage["content"])
        val toolCalls = assistantMessage["tool_calls"]!!.jsonArray
        assertEquals(1, toolCalls.size)
        assertEquals(
            "weather",
            toolCalls[0].jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.contentOrNull
        )
    }

    @Test
    fun testUnsupportedModeration() = runTest {
        val settings = DeepSeekClientSettings(
            baseUrl = "https://api.deepseek.com",
            chatCompletionsPath = "chat/completions",
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 12345,
                connectTimeoutMillis = 2345,
                socketTimeoutMillis = 3456
            )
        )
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, settings = settings, clock = FixedClock)

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hi!") }
        val ex = assertFailsWith<UnsupportedOperationException> {
            client.moderate(prompt, DeepSeekModels.DeepSeekV4Flash)
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
        val client = DeepSeekLLMClient(httpClientFactory = KtorKoogHttpClient.Factory(http), apiKey = key, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val response = client.execute(prompt, DeepSeekModels.DeepSeekV4Flash, tools = emptyList())
        assertEquals(1, response.parts.size, "Response should have once response")
        assertIs<MessagePart.Text>(response.parts[0], "Response should be assistant message")
        assertEquals(35, response.metaInfo.inputTokensCount)
        assertEquals(191, response.metaInfo.outputTokensCount)
        assertEquals(226, response.metaInfo.totalTokensCount)
    }
}
