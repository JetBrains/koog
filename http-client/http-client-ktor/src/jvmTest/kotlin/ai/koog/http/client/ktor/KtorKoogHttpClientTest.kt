package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.test.BaseKoogHttpClientTest
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

@Execution(ExecutionMode.SAME_THREAD)
class KtorKoogHttpClientTest : BaseKoogHttpClientTest() {
    override fun createClient(): KoogHttpClient {
        val baseClient = HttpClient(CIO) {}
        return KoogHttpClient.fromKtorClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            baseClient = baseClient
        ) {
            install(SSE)
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
            install(ContentNegotiation) {
                json(Json)
            }
        }
    }

    @Test
    override fun `test return success string response on get`() =
        super.`test return success string response on get`()

    @Test
    override fun `test return success string response on post`() =
        super.`test return success string response on post`()

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
}
