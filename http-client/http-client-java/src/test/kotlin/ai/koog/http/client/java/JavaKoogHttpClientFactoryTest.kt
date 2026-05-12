package ai.koog.http.client.java

import ai.koog.http.client.KoogHttpClient
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.SAME_THREAD)
class JavaKoogHttpClientFactoryTest : JavaKoogHttpClientTestBase() {
    override fun createClient(): KoogHttpClient {
        return JavaKoogHttpClient.Factory().create(clientName = "TestClient")
    }
}
