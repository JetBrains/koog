package ai.koog.prompt.executor.llms.all

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

internal data class MockResponse(
    val content: String,
    val status: HttpStatusCode,
)

internal fun createMockHttpClient(responses: Map<String, MockResponse>): HttpClient = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            val url = request.url.toString()
            val mock = responses[url] ?: error("No mock for $url")
            respond(
                content = mock.content,
                status = mock.status,
                headers = headersOf("Content-Type" to listOf("application/json"))
            )
        }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}