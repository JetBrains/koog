package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.net.http.HttpClient

@Execution(ExecutionMode.SAME_THREAD)
class JavaKoogHttpClientTest : JavaKoogHttpClientTestBase() {
    override fun createClient(): KoogHttpClient {
        return KoogHttpClient.fromJavaHttpClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            httpClient = HttpClient.newHttpClient(),
            json = Json
        )
    }
}
