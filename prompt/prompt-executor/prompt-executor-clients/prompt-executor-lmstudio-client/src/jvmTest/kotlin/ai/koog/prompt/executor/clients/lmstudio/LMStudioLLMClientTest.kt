package ai.koog.prompt.executor.clients.lmstudio

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class LMStudioLLMClientTest {

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val localModel = lmStudioModel("qwen/qwen3-1.7b")

    //language=json
    private val chatCompletionBody = """
        {
          "id": "chatcmpl-local",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "qwen/qwen3-1.7b",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "hi from lmstudio"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 5, "completion_tokens": 5}
        }
    """.trimIndent()

    // LM Studio's /v1/models response is missing `created` and `owned_by` on some builds.
    //language=json
    private val modelsBody = """
        {
          "object": "list",
          "data": [
            { "id": "qwen/qwen3-1.7b" },
            { "id": "llama-3.2-3b-instruct", "object": "model" }
          ]
        }
    """.trimIndent()

    @Test
    fun testClientReportsLMStudioProvider() {
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }) {}
        val client = LMStudioLLMClient(baseClient = http, clock = FixedClock)
        assertEquals(LLMProvider.LMStudio, client.llmProvider())
    }

    @Test
    fun testChatCompletionHitsLMStudioEndpoint() = runTest {
        var seenUrl: String? = null
        val engine = MockEngine { request ->
            seenUrl = request.url.toString()
            respond(
                content = chatCompletionBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val http = HttpClient(engine) {}
        val client = LMStudioLLMClient(baseClient = http, clock = FixedClock)

        val prompt = Prompt.build(id = "p", clock = FixedClock) {
            user("ping")
        }

        val responses = client.execute(prompt, localModel)

        assertEquals(1, responses.size)
        val assistant = assertIs<Message.Assistant>(responses[0])
        assertEquals("hi from lmstudio", assistant.content)
        assertEquals("http://localhost:1234/v1/chat/completions", seenUrl)
    }

    @Test
    fun testListModelsToleratesMissingOptionalFields() = runTest {
        val engine = MockEngine {
            respond(
                content = modelsBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val http = HttpClient(engine) {}
        val client = LMStudioLLMClient(baseClient = http, clock = FixedClock)

        val models = client.models()

        assertEquals(2, models.size)
        assertTrue(models.all { it.provider == LLMProvider.LMStudio })
        assertEquals("qwen/qwen3-1.7b", models[0].id)
        assertEquals("llama-3.2-3b-instruct", models[1].id)
    }
}
