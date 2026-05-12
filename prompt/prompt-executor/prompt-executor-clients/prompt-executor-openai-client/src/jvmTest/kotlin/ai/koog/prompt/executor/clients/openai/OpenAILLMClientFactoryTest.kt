package ai.koog.prompt.executor.clients.openai

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.test.utils.CapturingKoogHttpClient
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenAILLMClientFactoryTest {
    @Test
    fun testConstructorPassesProviderConfigurationToHttpClientFactory() {
        val factory = CapturingFactory()
        val settings = OpenAIClientSettings(
            baseUrl = "https://openai.test",
            timeoutConfig = ConnectionTimeoutConfig(
                requestTimeoutMillis = 1_000,
                connectTimeoutMillis = 2_000,
                socketTimeoutMillis = 3_000
            )
        )

        OpenAILLMClient(
            apiKey = "test-key",
            settings = settings,
            httpClientFactory = factory
        )

        assertEquals("OpenAILLMClient", factory.clientName)
        assertEquals("https://openai.test", factory.baseUrl)
        assertEquals(mapOf("Authorization" to "Bearer test-key"), factory.headers)
        assertEquals(emptyMap(), factory.queryParameters)
        assertEquals(1_000L, factory.requestTimeoutMillis)
        assertEquals(2_000L, factory.connectTimeoutMillis)
        assertEquals(3_000L, factory.socketTimeoutMillis)
    }

    private class CapturingFactory : KoogHttpClient.Factory {
        lateinit var clientName: String
        lateinit var baseUrl: String
        lateinit var headers: Map<String, String>
        lateinit var queryParameters: Map<String, String>
        var requestTimeoutMillis: Long = 0
        var connectTimeoutMillis: Long = 0
        var socketTimeoutMillis: Long = 0

        override fun create(
            clientName: String,
            baseUrl: String,
            headers: Map<String, String>,
            queryParameters: Map<String, String>,
            requestTimeoutMillis: Long,
            connectTimeoutMillis: Long,
            socketTimeoutMillis: Long,
            json: Json
        ): KoogHttpClient {
            this.clientName = clientName
            this.baseUrl = baseUrl
            this.headers = headers
            this.queryParameters = queryParameters
            this.requestTimeoutMillis = requestTimeoutMillis
            this.connectTimeoutMillis = connectTimeoutMillis
            this.socketTimeoutMillis = socketTimeoutMillis
            return CapturingKoogHttpClient(clientName) { error("No HTTP call expected") }
        }
    }
}
