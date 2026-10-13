package ai.koog.prompt.executor.ollama.client

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaToolCallDTO
import ai.koog.prompt.message.MessagePart
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OllamaClientTest {

    @Test
    fun testTotalRequiresBothTokenCounts() = runTest {
        val cases = listOf(
            Triple(5, 3, 8),
            Triple(0, 0, 0),
            Triple(0, 3, 3),
            Triple(5, 0, 5),
            Triple(5, null, null),
            Triple(null, 3, null),
            Triple(null, null, null),
            Triple(0, null, null),
            Triple(null, 0, null),
        )
        for ((input, output, total) in cases) {
            val counts = buildList {
                input?.let { add("\"prompt_eval_count\":$it") }
                output?.let { add("\"eval_count\":$it") }
            }.joinToString(separator = ",", prefix = if (input != null || output != null) "," else "")
            val mockEngine = MockEngine {
                respond(
                    content = """{"model":"llama3.2","message":{"role":"assistant","content":"Hello"},"done":true$counts}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
                )
            }
            val client = OllamaClient(httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockEngine)))
            try {
                val result = client.execute(prompt("usage") { }, OllamaModels.Meta.LLAMA_3_2)
                val case = "input=$input, output=$output"
                assertEquals(input, result.metaInfo.inputTokensCount, case)
                assertEquals(output, result.metaInfo.outputTokensCount, case)
                assertEquals(total, result.metaInfo.totalTokensCount, case)
            } finally {
                client.close()
            }
        }
    }

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
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        val responses = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertEquals(2, responses.parts.size)

        val textMessage = assertIs<MessagePart.Text>(responses.parts[0])
        assertEquals(responseContent, textMessage.text)

        val toolCallPart = assertIs<MessagePart.Tool.Call>(responses.parts[1])
        assertEquals(toolName, toolCallPart.tool)
        assertTrue(toolCallPart.args.contains("London"))
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
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockEngine))
        )

        val exception = assertFailsWith<LLMClientException> {
            ollamaClient.getModelOrNull("missing-model", pullIfMissing = true)
        }

        assertTrue(exception.message.orEmpty().contains(errorMessage))
    }
}
