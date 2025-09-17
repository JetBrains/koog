package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.list
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

// Ordinary text response from Gemini
private val response = """
    {
      "candidates" : [ {
        "content" : {
          "parts" : [ {
            "text" : "pong"
          } ],
          "role" : "model"
        },
        "finishReason" : "STOP",
        "index" : 0
      } ],
      "usageMetadata" : {
        "promptTokenCount" : 456,
        "candidatesTokenCount" : 1,
        "totalTokenCount" : 457,
        "promptTokensDetails" : [ {
          "modality" : "TEXT",
          "tokenCount" : 456
        } ]
      },
      "modelVersion" : "gemini-2.5-flash",
      "responseId" : "Vk_aBRaCaDABRAIPrvGj2Q8"
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
    fun `createGoogleRequest includes maxOutputTokens in request if prompt specifies max tokens`() = runTest {
        val customMax = 1234
        val p =
            Prompt.build("test", params = ai.koog.prompt.params.LLMParams(maxTokens = customMax)) {
                user("Hello")
            }

        val capturedBody = executeAndCaptureRequestBody(p, GoogleModels.Gemini2_5Flash)

        val json = Json.parseToJsonElement(capturedBody).jsonObject
        val genCfg = json["generationConfig"]!!.jsonObject
        val max = genCfg["maxOutputTokens"]!!.jsonPrimitive.int
        assertEquals(customMax, max, "maxOutputTokens should be populated from prompt")
    }

    @Test
    fun `createGoogleRequest does not include maxOutputTokens in request if prompt does not specify specify max tokens`() =
        runTest {
            val prompt = Prompt.build("test") { user("Hello") }
            val model = GoogleModels.Gemini2_5Flash.copy(maxOutputTokens = null)

            val capturedBody: String = executeAndCaptureRequestBody(prompt, model)

            val json = Json.parseToJsonElement(capturedBody).jsonObject
            val generationConfig = json["generationConfig"]!!.jsonObject
            assertEquals(
                false,
                generationConfig.containsKey("maxOutputTokens"),
                "maxOutputTokens should not be present in the request"
            )
        }

    private suspend fun executeAndCaptureRequestBody(p: Prompt, modelWithoutMax: LLModel): String {
        var capturedBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = ByteReadChannel(response),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = GoogleLLMClient(apiKey = "test-key", baseClient = HttpClient(mockEngine))

        client.execute(prompt = p, model = modelWithoutMax)
        return capturedBody!!
    }
}
