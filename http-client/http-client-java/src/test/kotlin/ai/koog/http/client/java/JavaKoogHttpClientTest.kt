package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.test.BaseKoogHttpClientTest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.net.http.HttpClient
import kotlin.test.Test

@Execution(ExecutionMode.SAME_THREAD)
class JavaKoogHttpClientTest : BaseKoogHttpClientTest() {
    override fun createClient(): KoogHttpClient {
        return KoogHttpClient.fromJavaHttpClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            httpClient = HttpClient.newHttpClient(),
            json = Json
        )
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
