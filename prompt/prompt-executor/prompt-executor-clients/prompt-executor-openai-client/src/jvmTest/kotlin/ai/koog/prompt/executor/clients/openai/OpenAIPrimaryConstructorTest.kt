package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenAIPrimaryConstructorTest {
    private val responseJson = """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "created": 1716920005,
          "model": "gpt-4o",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Hello from KoogHttpClient"
              },
              "finish_reason": "stop"
            }
          ],
          "usage": {"total_tokens": 10, "prompt_tokens": 4, "completion_tokens": 6}
        }
    """.trimIndent()

    @Test
    fun `primary constructor should execute through provided koog http client`() = runTest {
        val transport = CapturingKoogHttpClient { responseType ->
            when (responseType) {
                String::class -> responseJson
                else -> error("Unexpected response type: $responseType")
            }
        }
        val client = OpenAILLMClient(
            settings = OpenAIClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )

        val responses = client.execute(
            prompt = prompt("test") { user("Hello?") },
            model = OpenAIModels.Chat.GPT4o
        )

        assertEquals("v1/chat/completions", transport.lastPath)
        assertEquals(LLMProvider.OpenAI, client.llmProvider())
        assertEquals("""{"role":"user","content":"Hello?"}""", transport.lastRequest!!.substringAfter("\"messages\":[").substringBefore("]"))
        assertEquals(1, responses.size)
        val message = assertIs<Message.Assistant>(responses.single())
        assertEquals("Hello from KoogHttpClient", message.content)
    }

    private class CapturingKoogHttpClient(
        private val responder: (KClass<*>) -> Any
    ) : KoogHttpClient {
        override val clientName: String = "CapturingOpenAIClient"
        var lastPath: String? = null
        var lastRequest: String? = null

        override suspend fun <R : Any> get(path: String, responseType: KClass<R>, parameters: Map<String, String>): R {
            error("GET is not expected in this test")
        }

        override suspend fun <T : Any, R : Any> post(
            path: String,
            request: T,
            requestBodyType: KClass<T>,
            responseType: KClass<R>,
            parameters: Map<String, String>
        ): R {
            lastPath = path
            lastRequest = request.toString()
            @Suppress("UNCHECKED_CAST")
            return responder(responseType) as R
        }

        override fun <T : Any, R : Any, O : Any> sse(
            path: String,
            request: T,
            requestBodyType: KClass<T>,
            dataFilter: (String?) -> Boolean,
            decodeStreamingResponse: (String) -> R,
            processStreamingChunk: (R) -> O?,
            parameters: Map<String, String>
        ): Flow<O> = emptyFlow()

        override fun close() = Unit
    }
}
