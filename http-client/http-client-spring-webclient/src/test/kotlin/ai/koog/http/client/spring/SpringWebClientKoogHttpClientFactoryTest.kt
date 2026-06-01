package ai.koog.http.client.spring

import ai.koog.http.client.KoogHttpClient
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class SpringWebClientKoogHttpClientFactoryTest : SpringWebClientKoogHttpClientTestBase() {
    override fun createClient(): KoogHttpClient {
        return SpringWebClientKoogHttpClient.Factory().create(clientName = "TestClient")
    }
}
