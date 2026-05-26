package ai.koog.http.client.okhttp

import ai.koog.http.client.KoogHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class HttpKoogHttpClientTest : OkHttpKoogHttpClientTestBase() {
    override fun createClient(): KoogHttpClient {
        return KoogHttpClient.fromOkHttpClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            okHttpClient = OkHttpClient(),
            json = Json
        )
    }
}
