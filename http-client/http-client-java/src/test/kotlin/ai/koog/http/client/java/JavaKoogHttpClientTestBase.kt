package ai.koog.http.client.java

import ai.koog.http.client.post
import ai.koog.http.client.test.BaseKoogHttpClientTest
import ai.koog.http.client.test.MockWebServer
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class JavaKoogHttpClientTestBase : BaseKoogHttpClientTest() {

    @Test
    override fun `test return success string response on get`() =
        super.`test return success string response on get`()

    @Test
    override fun `test return success string response on post`() =
        super.`test return success string response on post`()

    @Test
    override fun `test post request headers override inferred string content type`() =
        super.`test post request headers override inferred string content type`()

    @Test
    override fun `test post JSON request and get JSON response`() =
        super.`test post JSON request and get JSON response`()

    @Test
    override fun `test handle on non-success status`() =
        super.`test handle on non-success status`()

    @Test
    override fun `test get SSE flow and collect events`() =
        super.`test get SSE flow and collect events`()

    @Test
    override fun `test filter SSE events`() =
        super.`test filter SSE events`()

    @Test
    override fun `test return success string response on get with parameters`() {
        super.`test return success string response on get with parameters`()
    }

    @Test
    override fun `test return success string response on post with parameters`() {
        super.`test return success string response on post with parameters`()
    }

    @Test
    override fun `test lines emits non-blank lines`() =
        super.`test lines emits non-blank lines`()

    @Test
    override fun `test lines request headers override inferred string content type`() =
        super.`test lines request headers override inferred string content type`()

    @Test
    override fun `test lines skips blank lines`() =
        super.`test lines skips blank lines`()

    @Test
    override fun `test lines emits nothing for empty body`() =
        super.`test lines emits nothing for empty body`()

    @Test
    override fun `test lines surfaces non-2xx as KoogHttpClientException`() =
        super.`test lines surfaces non-2xx as KoogHttpClientException`()

    @Test
    override fun `test lines propagates cancellation`() =
        super.`test lines propagates cancellation`()

    @Test
    fun testJavaFactoryAppliesBaseUrlAndDefaultQueryParameters() = runTest {
        val mockServer = MockWebServer()
        try {
            mockServer.start(
                postEndpoints = listOf(
                    MockWebServer.PostEndpointConfig(
                        path = "/api/echo",
                        responseBody = """{"response":"Okay"}""",
                        statusCode = HttpStatusCode.OK,
                        contentType = ContentType.Application.Json,
                        expectedParameters = mapOf("tenant" to "acme", "request" to "one")
                    )
                )
            )

            val client = JavaKoogHttpClient.Factory().create(
                clientName = "TestClient",
                baseUrl = mockServer.url("/api"),
                headers = mapOf("Authorization" to "Bearer token"),
                queryParameters = mapOf("tenant" to "acme"),
                json = Json
            )

            val result = client.post<BaseKoogHttpClientTest.TestRequest, BaseKoogHttpClientTest.TestResponse>(
                path = "echo",
                request = BaseKoogHttpClientTest.TestRequest("hello"),
                parameters = mapOf("request" to "one")
            )

            assertEquals("Okay", result.response)
            client.close()
        } finally {
            mockServer.stop()
        }
    }
}
