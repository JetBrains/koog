package ai.koog.http.client.okhttp

import ai.koog.http.client.KoogHttpClient
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class OkHttpKoogHttpClientFactoryTest : OkHttpKoogHttpClientTestBase() {
    override fun createClient(): KoogHttpClient {
        return OkHttpKoogHttpClient.Factory().create(clientName = "TestClient")
    }
}
