package ai.koog.prompt.executor.clients.google

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.models.GooglePart
import ai.koog.prompt.executor.clients.google.models.GoogleRequest
import ai.koog.prompt.executor.clients.google.models.GoogleResponse
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GooglePrimaryConstructorTest {
    private val responseJson = """
        {
          "candidates": [
            {
              "content": {
                "role": "model",
                "parts": [
                  {
                    "text": "Hello from Google KoogHttpClient"
                  }
                ]
              },
              "finishReason": "STOP",
              "index": 0
            }
          ],
          "usageMetadata": {
            "promptTokenCount": 3,
            "totalTokenCount": 8
          },
          "modelVersion": "gemini-2.5-pro"
        }
    """.trimIndent()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun `primary constructor should execute through koog http client`() = runTest {
        val transport = CapturingKoogHttpClient { _ ->
            json.decodeFromString<GoogleResponse>(responseJson)
        }
        val client = GoogleLLMClient(
            settings = GoogleClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )

        val responses = client.execute(
            prompt = prompt("test") { user("Hello?") },
            model = GoogleModels.Gemini2_5Pro
        )

        assertEquals("v1beta/models/gemini-2.5-pro:generateContent", transport.lastPath)
        assertEquals(LLMProvider.Google, client.llmProvider())
        val request = transport.lastRequest
        assertTrue(request is GoogleRequest)
        val userPart = request.contents.single().parts!!.single() as GooglePart.Text
        assertEquals("Hello?", userPart.text)
        val message = assertIs<Message.Assistant>(responses.single())
        assertEquals("Hello from Google KoogHttpClient", message.content)
    }

    private class CapturingKoogHttpClient(
        private val responder: (KClass<*>) -> Any
    ) : KoogHttpClient {
        override val clientName: String = "CapturingGoogleClient"
        var lastPath: String? = null
        var lastRequest: Any? = null

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
            lastRequest = request
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
