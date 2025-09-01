package deepseek

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeepSeekLLMClientTest {

    object FixedClock : Clock {
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
        val client = DeepSeekLLMClient(apiKey = key, settings = settings, baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hello") }

        val responses = client.execute(prompt, DeepSeekModels.DeepSeekChat)

        assertTrue(capturedUrl.startsWith("https://api.deepseek.com/"))
        assertTrue(capturedUrl.endsWith("chat/completions"))
        assertEquals(HttpMethod.Post, capturedMethod)
        assertEquals("Bearer $key", capturedAuth)
        assertEquals(1, responses.size)
        val text = (responses.first() as Message.Assistant).content
        assertEquals(content, text)
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
        val client = DeepSeekLLMClient(apiKey = key, baseClient = http, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val choices = client.executeMultipleChoices(prompt, DeepSeekModels.DeepSeekChat, tools = emptyList())
        assertEquals(2, choices.size, "Response should have two choices")
        assertEquals(optionA, (choices[0].first() as Message.Assistant).content, "$optionA should be first")
        assertEquals(optionB, (choices[1].first() as Message.Assistant).content, "$optionB should be second")
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
        val client = DeepSeekLLMClient(apiKey = key, baseClient = http, clock = FixedClock)
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

        val responses = client.execute(prompt, DeepSeekModels.DeepSeekChat)
        assertEquals(1, responses.size, "Response should have one choice")
        assertNotNull(capturedBody, "Captured body should not be null")
        assertTrue(capturedBody.contains("\"response_format\""), "Response body should contain response_format")
        assertTrue(capturedBody.contains("\"json_schema\""), "Response body should contain json_schema")
        assertTrue(
            responses.first().content.contains("{\"name\":\"Alice\"}"),
            "Response should contain JSON string [{\"name\":\"Alice\"}]"
        )
    }

    @Test
    fun testExecuteStreaming() = runTest {
        val client = DeepSeekLLMClient(apiKey = "test-key", baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p-stream", clock = FixedClock) { user("Stream it") }
        val flow = client.executeStreaming(prompt, DeepSeekModels.DeepSeekChat)
        // For now, we'd only verify that streaming flow can be created
        // as MockEngine does not support Ktor SSE end-to-end streaming reliably in tests
        assertNotNull(flow, "Flow should not be null")
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
        val client = DeepSeekLLMClient(apiKey = key, settings = settings, baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p1", clock = FixedClock) { user("Hi!") }
        val ex = assertFailsWith<UnsupportedOperationException> {
            client.moderate(prompt, DeepSeekModels.DeepSeekChat)
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
        val client = DeepSeekLLMClient(apiKey = key, baseClient = http, clock = FixedClock)
        val prompt = Prompt.build(id = "p-multi", clock = FixedClock) {
            user("Give two options")
        }.withUpdatedParams {
            temperature = 0.2
        }

        val responses = client.execute(prompt, DeepSeekModels.DeepSeekChat, tools = emptyList())
        assertEquals(1, responses.size, "Response should have once response")
        val response = responses.first()
        assertIs<Message.Assistant>(response, "Response should be assistant message")
        assertEquals(35, response.metaInfo.inputTokensCount)
        assertEquals(191, response.metaInfo.outputTokensCount)
        assertEquals(226, response.metaInfo.totalTokensCount)
    }
}
