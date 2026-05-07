package ai.koog.http.client.ktor

import ai.koog.http.client.KoogHttpClient
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class KtorKoogHttpClientFactoryTest : KtorKoogHttpClientTestBase() {
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
