package ai.koog.utils.http

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class OkHttpKoogHttpClientTest {

    private val logger = KotlinLogging.logger("TestLogger")
    private val mockServer = MockWebServer()

    @AfterTest
    fun tearDown() {
        mockServer.stop()
    }

    @Test
    fun `test return success string response`() = runTest {
        val responseBody = "RESPONSE_OK"

        mockServer.start(
            endpoints = listOf(
                MockWebServer.EndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Text.Plain
                )
            )
        )

        val client = KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = logger,
            okHttpClient = OkHttpClient()
        )

        val result: String = client.post(
            path = mockServer.url("/echo"),
            request = "PAYLOAD"
        )

        assertEquals(responseBody, result)
    }

    @Test
    fun `test post JSON request and get JSON response`() = runTest {
        @Serializable
        data class Request(val request: String)

        @Serializable
        data class Response(val response: String)

        val responseBody = """{"response":"Okay"}"""

        mockServer.start(
            endpoints = listOf(
                MockWebServer.EndpointConfig(
                    path = "/echo",
                    responseBody = responseBody,
                    statusCode = HttpStatusCode.OK,
                    contentType = ContentType.Application.Json
                )
            )
        )

        val client = KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = logger,
            okHttpClient = OkHttpClient(),
            json = Json
        )

        val result: Response = client.post(
            path = mockServer.url("/echo"),
            request = Request("How are you?"),
            requestBodyType = Request::class,
            responseType = Response::class
        )

        assertEquals("Okay", result.response)
    }

    @Test
    fun `test handle on non-success status`() = runTest {
        mockServer.start(
            endpoints = listOf(
                MockWebServer.EndpointConfig(
                    path = "/fail",
                    responseBody = "Bad things",
                    statusCode = HttpStatusCode.BadRequest,
                    contentType = ContentType.Text.Plain
                )
            )
        )

        val client = KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = logger,
            okHttpClient = OkHttpClient()
        )

        try {
            client.post(
                path = mockServer.url("/fail"),
                request = "PAYLOAD",
            )
            fail("Expected an exception for non-success status")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message) {
                assertContains(it, "Error from TestClient API")
                assertContains(it, "400")
                assertContains(it, "Bad things")
            }
        }
    }

    @Test
    fun `test get SSE flow and collect events`() = runTest {
        val events = listOf("event1", "event2", "event3")

        mockServer.start(
            sseEndpoints = listOf(
                MockWebServer.SSEEndpointConfig(
                    path = "/stream",
                    events = events
                )
            )
        )

        val client = KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = logger,
            okHttpClient = OkHttpClient()
        )

        val flow = client.sse(
            path = mockServer.url("/stream"),
            request = "{}",
            requestBodyType = String::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = { it },
            processStreamingChunk = { it }
        )

        val collected = flow.toList()

        assertEquals(events.size, collected.size)
        assertEquals(events, collected)
    }

    @Test
    fun `test filter SSE events`() = runTest {
        val events = listOf("event1", "[DONE]", "event2", "[DONE]", "event3")

        mockServer.start(
            sseEndpoints = listOf(
                MockWebServer.SSEEndpointConfig(
                    path = "/stream",
                    events = events
                )
            )
        )

        val client = KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = logger,
            okHttpClient = OkHttpClient()
        )

        val flow = client.sse(
            path = mockServer.url("/stream"),
            request = "{}",
            requestBodyType = String::class,
            dataFilter = { it != "[DONE]" },
            decodeStreamingResponse = { it },
            processStreamingChunk = { it }
        )

        val collected = flow.toList()

        // Only non-[DONE] events should be collected
        assertEquals(3, collected.size)
        assertEquals(listOf("event1", "event2", "event3"), collected)
    }
}
