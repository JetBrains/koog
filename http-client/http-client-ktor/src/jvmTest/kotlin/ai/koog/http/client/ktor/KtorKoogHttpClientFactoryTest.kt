package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.get
import ai.koog.http.client.post
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals

@Execution(ExecutionMode.SAME_THREAD)
class KtorKoogHttpClientFactoryTest : KtorKoogHttpClientTestBase() {
    @Test
    fun testRequestHeadersWithoutDefaults() = runTest {
        for (operation in listOf("get", "post", "lines")) {
            val requests = mutableListOf<HttpRequestData>()
            HttpClient(
                MockEngine { request ->
                    requests += request
                    respond("ok")
                }
            ).use { baseClient ->
                ktorClient(baseClient, baseUrl = "https://example.test").use { client ->
                    sendRequest(client, operation, mapOf("X-Request-Id" to "request-id"))
                    assertEquals(listOf("request-id"), requests.single().headers.getAll("X-Request-Id"), operation)
                }
            }
        }
    }

    @Test
    fun testRequestHeadersOverrideDefaultsWithoutAffectingLaterRequests() = runTest {
        for (operation in listOf("get", "post", "lines")) {
            val requests = mutableListOf<HttpRequestData>()
            HttpClient(
                MockEngine { request ->
                    requests += request
                    respond("ok")
                }
            ).use { baseClient ->
                ktorClient(
                    baseClient,
                    baseUrl = "https://example.test",
                    headers = mapOf(
                        HttpHeaders.Authorization to "Bearer default",
                        HttpHeaders.ContentType to "application/default",
                        "X-Default" to "retained"
                    )
                ).use { client ->
                    sendRequest(
                        client,
                        operation,
                        mapOf("aUtHoRiZaTiOn" to "Bearer request", "cOnTeNt-TyPe" to "application/request")
                    )
                    client.get<String>("later")

                    val overridden = requests[0]
                    assertEquals(listOf("Bearer request"), overridden.headers.getAll(HttpHeaders.Authorization), operation)
                    assertEquals("application/request", contentType(overridden), operation)
                    assertEquals(listOf("retained"), overridden.headers.getAll("X-Default"), operation)
                    val later = requests[1]
                    assertEquals(listOf("Bearer default"), later.headers.getAll(HttpHeaders.Authorization), operation)
                    assertEquals("application/default", contentType(later), operation)
                    assertEquals(listOf("retained"), later.headers.getAll("X-Default"), operation)
                }
            }
        }
    }

    @Test
    fun testJsonContentTypeRemainsTheDefault() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        HttpClient(
            MockEngine { request ->
                requests += request
                respond("ok")
            }
        ).use { baseClient ->
            ktorClient(baseClient, baseUrl = "https://example.test").use { client ->
                client.post<String, String>("messages", "{}")
                assertEquals(ContentType.Application.Json.toString(), contentType(requests.single()))
            }
        }
    }

    private fun contentType(request: HttpRequestData): String? =
        request.body.contentType?.toString() ?: request.headers[HttpHeaders.ContentType]

    private suspend fun sendRequest(client: KoogHttpClient, operation: String, headers: Map<String, String>) {
        when (operation) {
            "get" -> client.get<String>("messages", headers = headers)
            "post" -> client.post<String, String>("messages", "{}", headers = headers)
            "lines" -> client.lines("messages", "{}", String::class, headers = headers).toList()
        }
    }

    override fun createClient(): KoogHttpClient {
        return KtorKoogHttpClient.Factory().create(clientName = "TestClient")
    }

    override fun ktorClient(
        baseClient: HttpClient,
        baseUrl: String,
        json: Json,
        headers: Map<String, String>,
        queryParameters: Map<String, String>,
        withSse: Boolean
    ): KtorKoogHttpClient {
        return KtorKoogHttpClient.Factory(baseClient, withSse).create(
            clientName = "TestClient",
            baseUrl = baseUrl,
            json = json,
            headers = headers,
            queryParameters = queryParameters,
        )
    }
}
