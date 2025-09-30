package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatMessageDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.tokenizer.PromptTokenizer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContextWindowStrategyTest {
    @Test
    fun `test None strategy`() = runTest {
        val mockServer = MockOllamaChatServer { request -> makeDummyResponse(request) }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine),
            contextWindowStrategy = ContextWindowStrategy.Companion.None,
        )

        ollamaClient.execute(
            prompt = prompt("test-prompt") { },
            model = OllamaModels.Meta.LLAMA_3_2,
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(requestHistory.size, 1)

        val response = requestHistory.first()
        assertNotNull(response.options)
        assertNull(response.options.numCtx)
    }

    @Test
    fun `test Fixed strategy`() = runTest {
        val mockServer = MockOllamaChatServer { request -> makeDummyResponse(request) }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine),
            contextWindowStrategy = ContextWindowStrategy.Companion.Fixed(42),
        )

        ollamaClient.execute(
            prompt = prompt("test-prompt") { },
            model = OllamaModels.Meta.LLAMA_3_2,
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(requestHistory.size, 1)

        val response = requestHistory.first()
        assertNotNull(response.options)
        assertEquals(42, response.options.numCtx)
    }

    @Test
    fun `test FitPrompt strategy with tokenizer`() = runTest {
        val mockServer = MockOllamaChatServer { request -> makeDummyResponse(request) }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine),
            contextWindowStrategy = ContextWindowStrategy.Companion.FitPrompt(
                promptTokenizer = object : PromptTokenizer {
                    override fun tokenCountFor(message: Message): Int = error("Not needed")
                    override fun tokenCountFor(prompt: Prompt): Int = 3000
                },
                contextChunkSize = 1024,
                minimumChunkCount = 2
            ),
        )

        ollamaClient.execute(
            prompt = prompt("test-prompt") { },
            model = OllamaModels.Meta.LLAMA_3_2,
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(requestHistory.size, 1)

        val response = requestHistory.first()
        assertNotNull(response.options)
        assertEquals(3072, response.options.numCtx)
    }

    @Test
    fun `test FitPrompt strategy without tokenizer and no previous token usage`() = runTest {
        val mockServer = MockOllamaChatServer { request -> makeDummyResponse(request) }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine),
            contextWindowStrategy = ContextWindowStrategy.Companion.FitPrompt(
                promptTokenizer = null,
                contextChunkSize = 1024,
                minimumChunkCount = 2
            ),
        )

        ollamaClient.execute(
            prompt = prompt("test-prompt") { },
            model = OllamaModels.Meta.LLAMA_3_2,
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(requestHistory.size, 1)

        val response = requestHistory.first()
        assertNotNull(response.options)
        assertEquals(2048, response.options.numCtx)
    }

    @Test
    fun `test FitPrompt strategy without tokenizer and existing token usage`() = runTest {
        val mockServer = MockOllamaChatServer { request -> makeDummyResponse(request) }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine),
            contextWindowStrategy = ContextWindowStrategy.Companion.FitPrompt(
                promptTokenizer = null,
                contextChunkSize = 1024,
                minimumChunkCount = 2
            ),
        )

        ollamaClient.execute(
            prompt = prompt("test-prompt") {
                message(
                    Message.Assistant(
                        "Dummy message",
                        metaInfo = ResponseMetaInfo(
                            timestamp = Clock.System.now(),
                            totalTokensCount = 5000,
                        )
                    )
                )
            },
            model = OllamaModels.Meta.LLAMA_3_2,
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(requestHistory.size, 1)

        val response = requestHistory.first()
        assertNotNull(response.options)
        assertEquals(5120, response.options.numCtx)
    }

    @Test
    fun `test FitPrompt strategy with tokenizer and too long prompt`() = runTest {
        val mockServer = MockOllamaChatServer { request -> makeDummyResponse(request) }

        val ollamaClient = OllamaClient(
            baseClient = HttpClient(mockServer.mockEngine),
            contextWindowStrategy = ContextWindowStrategy.Companion.FitPrompt(
                promptTokenizer = object : PromptTokenizer {
                    override fun tokenCountFor(message: Message): Int = error("Not needed")
                    override fun tokenCountFor(prompt: Prompt): Int = 9000
                },
                contextChunkSize = 1024,
                minimumChunkCount = 2
            ),
        )

        ollamaClient.execute(
            prompt = prompt("test-prompt") { },
            model = OllamaModels.Meta.LLAMA_3_2.copy(
                contextLength = 8192
            ),
        )

        val requestHistory = mockServer.requestHistory
        assertEquals(requestHistory.size, 1)

        val response = requestHistory.first()
        assertNotNull(response.options)
        assertEquals(8192, response.options.numCtx)
    }
}

private fun makeDummyResponse(
    request: OllamaChatRequestDTO,
    content: String = "OK",
    promptEvalCount: Int = 10,
    evalCount: Int = 100,
): OllamaChatResponseDTO = OllamaChatResponseDTO(
    model = request.model,
    message = OllamaChatMessageDTO(role = "assistant", content = content),
    done = true,
    promptEvalCount = promptEvalCount,
    evalCount = evalCount,
)

private class MockOllamaChatServer(
    private val handler: (OllamaChatRequestDTO) -> OllamaChatResponseDTO,
) {
    val mockEngine = MockEngine { requestData ->
        val request = requestData.extractChatRequest()
        val response = handler(request)
        respond(
            content = Json.encodeToString<OllamaChatResponseDTO>(response),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType to listOf("application/json")),
        )
    }

    val requestHistory: List<OllamaChatRequestDTO>
        get() = mockEngine.requestHistory.map { it.extractChatRequest() }

    private fun HttpRequestData.extractChatRequest(): OllamaChatRequestDTO {
        val requestContent = body as TextContent
        val requestBody = requestContent.text
        val request = Json.decodeFromString<OllamaChatRequestDTO>(requestBody)
        return request
    }
}
