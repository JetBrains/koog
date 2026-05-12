package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class KtorKoogHttpClientTest : KtorKoogHttpClientTestBase() {

    override fun createClient(): KoogHttpClient = ktorClient(baseClient = HttpClient(CIO) {})

    override fun ktorClient(
        baseClient: HttpClient,
        baseUrl: String,
        json: Json,
        headers: Map<String, String>,
        queryParameters: Map<String, String>,
        withSse: Boolean
    ): KoogHttpClient {
        return KoogHttpClient.fromKtorClient(
            clientName = "TestClient",
            logger = KotlinLogging.logger("TestLogger"),
            baseClient = baseClient,
            baseUrl = baseUrl,
            json = json,
            headers = headers,
            queryParameters = queryParameters,
            withSse = withSse
        )
    }
}
