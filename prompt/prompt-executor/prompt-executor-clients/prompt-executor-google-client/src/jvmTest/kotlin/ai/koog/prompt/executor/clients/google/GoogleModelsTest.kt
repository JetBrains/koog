package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import io.kotest.matchers.collections.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

// "Bad" request from Gemini with missing `parts` field
private val badRequest: String = """
    {
      "candidates": [
        {
          "content": {
            "role": "model"
          },
          "finishReason": "STOP",
          "index": 0
        }
      ],
      "usageMetadata": {
        "promptTokenCount": 36,
        "totalTokenCount": 146,
        "promptTokensDetails": [
          {
            "modality": "TEXT",
            "tokenCount": 36
          }
        ],
        "thoughtsTokenCount": 110
      },
      "modelVersion": "gemini-2.5-pro",
      "responseId": "B0esaJmqKv-0xN8P-dzlwQY"
    }
""".trimIndent()

class GoogleModelsTest {

    @Test
    fun `Google models should have Google provider`() {
        val models = GoogleModels.list()

        models.forEach { model ->
            assertSame(
                expected = LLMProvider.Google,
                actual = model.provider,
                message = "Google model ${model.id} doesn't have Google provider but ${model.provider}."
            )
        }
    }

    @Test
    fun `Test when FLASH_2_5 returns no parts GoogleLLMClient does not fail`() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(badRequest),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val googleClient = GoogleLLMClient(
            apiKey = "test-key",
            baseClient = HttpClient(mockEngine) // Ktor client would always respond with the json from above
        )

        val responses = googleClient.execute(
            prompt = prompt("test") { user("What is the capital of France?") },
            model = GoogleModels.Gemini2_5Flash
        )

        assertEquals(1, responses.size)
        // When no parts returned -- content should be interpreted as empty
        assertEquals("", responses.single().content)
        // Also let's check some other fields parsing
        assertEquals(Message.Role.Assistant, responses.single().role)
        assertEquals(36, responses.single().metaInfo.inputTokensCount)
        assertEquals(146, responses.single().metaInfo.totalTokensCount)
    }

    @Test
    fun `GoogleModels models should return all declared models`() {
        val reflectionModels = GoogleModels.list().map { it.id }

        val models = GoogleModels.models.map { it.id }

        assert(models.size == reflectionModels.size)

        reflectionModels.forEach { model ->
            models shouldContain model
        }
    }
}
