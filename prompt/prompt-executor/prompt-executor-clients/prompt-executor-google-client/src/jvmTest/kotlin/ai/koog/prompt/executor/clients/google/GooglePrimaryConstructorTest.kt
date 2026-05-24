package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.models.GooglePart
import ai.koog.prompt.executor.clients.google.models.GoogleRequest
import ai.koog.prompt.executor.clients.google.models.GoogleResponse
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.MessagePart
import ai.koog.test.utils.CapturingKoogHttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
        val transport = CapturingKoogHttpClient(clientName = "CapturingGoogleClient") { _ ->
            json.decodeFromString<GoogleResponse>(responseJson)
        }
        val client = GoogleLLMClient(
            settings = GoogleClientSettings(baseUrl = "https://unused.test"),
            httpClient = transport
        )

        val response = client.execute(
            prompt = prompt("test") { user("Hello?") },
            model = GoogleModels.Gemini2_5Pro
        )

        assertEquals("v1beta/models/gemini-2.5-pro:generateContent", transport.lastPath)
        assertEquals(LLMProvider.Google, client.llmProvider())
        val request = transport.lastRequest
        assertTrue(request is GoogleRequest)
        val userPart = request.contents.single().parts!!.single() as GooglePart.Text
        assertEquals("Hello?", userPart.text)
        val message = assertIs<MessagePart.Text>(response.parts.single())
        assertEquals("Hello from Google KoogHttpClient", message.text)
    }
}
