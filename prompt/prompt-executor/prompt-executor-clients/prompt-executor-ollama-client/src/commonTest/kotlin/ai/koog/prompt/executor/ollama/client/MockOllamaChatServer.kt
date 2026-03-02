package ai.koog.prompt.executor.ollama.client

import ai.koog.prompt.executor.ollama.client.dto.OllamaChatRequestDTO
import ai.koog.prompt.executor.ollama.client.dto.OllamaChatResponseDTO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json

internal class MockOllamaChatServer(
    private val handler: (OllamaChatRequestDTO) -> OllamaChatResponseDTO,
) {
    val mockEngine = MockEngine.Companion { requestData ->
        val request = requestData.extractChatRequest()
        val response = handler(request)
        respond(
            content = Json.encodeToString<OllamaChatResponseDTO>(response),
            status = HttpStatusCode.Companion.OK,
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
