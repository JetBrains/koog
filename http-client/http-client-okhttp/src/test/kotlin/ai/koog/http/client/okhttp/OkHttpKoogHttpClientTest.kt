package ai.koog.http.client.okhttp

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.test.BaseKoogHttpClientTest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test

@Execution(ExecutionMode.SAME_THREAD)
class OkHttpKoogHttpClientTest : BaseKoogHttpClientTest() {

    override fun createClient(): KoogHttpClient {
        return KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            okHttpClient = OkHttpClient(),
            json = Json
        )
    }

    @Test
    override fun `test return success string response`() =
        super.`test return success string response`()

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
