package ai.koog.prompt.executor.ollama.client

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaLogProbDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaToolCallDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaTopLogProbDTO
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    @Test
    fun testExecuteParsesLogProbs() = runTest {
        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(role = "assistant", content = "Hello"),
                done = true,
                logprobs = listOf(
                    OllamaLogProbDTO(
                        token = "Hello",
                        logprob = -0.12,
                        bytes = listOf(72, 101, 108, 108, 111),
                        topLogprobs = listOf(
                            OllamaTopLogProbDTO(token = "Hi", logprob = -1.45),
                            OllamaTopLogProbDTO(token = "Hello", logprob = -0.12),
                        ),
                    ),
                ),
            )
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        val response = ollamaClient.execute(
            prompt = prompt("test", OllamaParams(logprobs = true, topLogprobs = 2)) { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        val logprobs = assertNotNull(response.logprobs)
        assertEquals(1, logprobs.size)
        assertEquals("Hello", logprobs[0].token)
        assertEquals(-0.12, logprobs[0].logprob)
        assertEquals(listOf(72, 101, 108, 108, 111), logprobs[0].bytes)
        assertEquals(2, logprobs[0].topLogprobs.size)
        assertEquals("Hi", logprobs[0].topLogprobs[0].token)
    }

    @Test
    fun testExecuteReturnsNullLogProbsWhenAbsent() = runTest {
        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(role = "assistant", content = "Hello"),
                done = true,
            )
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        val response = ollamaClient.execute(
            prompt = prompt("test") { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        assertNull(response.logprobs)
    }

    @Test
    fun testExecuteSendsLogProbsRequestParams() = runTest {
        val mockServer = MockOllamaChatServer { request ->
            OllamaChatResponseDTO(
                model = request.model,
                message = OllamaChatMessageDTO(role = "assistant", content = "Hello"),
                done = true,
            )
        }

        val ollamaClient = OllamaClient(
            httpClientFactory = KtorKoogHttpClient.Factory(HttpClient(mockServer.mockEngine))
        )

        ollamaClient.execute(
            prompt = prompt("test", OllamaParams(logprobs = true, topLogprobs = 5)) { },
            model = OllamaModels.Meta.LLAMA_3_2
        )

        val sentRequest = mockServer.requestHistory.single()
        assertEquals(true, sentRequest.logprobs)
        assertEquals(5, sentRequest.topLogprobs)
    }

    @Test
    fun testTopLogprobsOutOfRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            OllamaParams(logprobs = true, topLogprobs = 21)
        }
    }

    @Test
    fun testTopLogprobsWithLogprobsDisabledIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            OllamaParams(logprobs = false, topLogprobs = 5)
        }
    }
}
