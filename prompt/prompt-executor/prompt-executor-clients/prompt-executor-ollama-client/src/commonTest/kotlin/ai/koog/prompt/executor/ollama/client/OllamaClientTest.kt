package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaToolCallDTO
import ai.koog.prompt.message.Message
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OllamaClientTest {

    @Test
    fun testExecuteWithContentAndToolCalls() = runTest {
        val responseContent = "I will check the weather for you."
        val toolName = "get_weather"
        val toolArgs = JsonObject(mapOf("city" to JsonPrimitive("London")))

        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(
                    role = "assistant",
                    content = responseContent,
                    toolCalls = listOf(
                        OllamaToolCallDTO(
                            function = OllamaToolCallDTO.Call(
                                name = toolName,
                                arguments = toolArgs
                            )
                        )
                    )
                ),
                done = true
            )
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine)
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(2, responses.size)

        // tool call should come first and message second
        val toolCallMessage = responses[0]
        assertTrue(toolCallMessage is Message.Tool.Call)
        assertEquals(toolName, toolCallMessage.tool)
        assertTrue(toolCallMessage.content.contains("London"))

        val assistantMessage = responses[1]
        assertTrue(assistantMessage is Message.Assistant)
        assertEquals(responseContent, assistantMessage.content)
    }

    @Test
    fun testGetModelOrNullReportsPullErrorResponse() = runTest {
        val errorMessage = "pull model manifest: file does not exist"
        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/tags" -> respond(
                    content = """{"models":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                )

                "/api/pull" -> respond(
                    content = """{"error":"$errorMessage"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                )

                else -> error("Unexpected request to ${request.url.encodedPath}")
            }
        }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockEngine)
        )

        val exception = assertFailsWith<LLMClientException> {
            ollamaClient.getModelOrNull("missing-model", pullIfMissing = true)
        }

        assertTrue(exception.message.orEmpty().contains(errorMessage))
    }
}
