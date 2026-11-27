package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.utils.io.use
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Checks what [addLangfuseExporter] puts on the wire by pointing it at a local HTTP sink.
 */
class LangfuseExporterHeadersTest {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testExporterSendsLangfuseV4IngestionHeader() {
        val receivedHeaders = CopyOnWriteArrayList<Map<String, List<String>>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            receivedHeaders.add(exchange.requestHeaders.mapKeys { (name, _) -> name.lowercase() })
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()

        try {
            // runBlocking rather than runTest: the batch span processor exports on real dispatchers,
            // on its own schedule, so the SDK must stay alive after the agent is closed.
            runBlocking {
                AIAgent(
                    promptExecutor = getMockExecutor { mockLLMAnswer("It is sunny in Paris.").asDefaultResponse },
                    llmModel = OpenAIModels.Chat.GPT4o,
                    systemPrompt = "You are a weather assistant.",
                ) {
                    install(OpenTelemetry) {
                        addLangfuseExporter(
                            langfuseUrl = "http://127.0.0.1:${server.address.port}",
                            langfusePublicKey = "pk-test",
                            langfuseSecretKey = "sk-test",
                        )
                    }
                }.use { agent -> agent.run("What's the weather in Paris?") }
            }

            val deadline = System.currentTimeMillis() + 30_000
            while (receivedHeaders.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50)
            }
            assertTrue(receivedHeaders.isNotEmpty(), "The exporter never sent a request")

            val headers = receivedHeaders.first()
            // Langfuse selects its v4 ingestion path by this header; without it the data takes the legacy path.
            assertEquals(listOf("4"), headers["x-langfuse-ingestion-version"])
            val expectedAuthorization = "Basic " + Base64.encode("pk-test:sk-test".encodeToByteArray())
            assertEquals(listOf(expectedAuthorization), headers["authorization"])
        } finally {
            server.stop(0)
        }
    }
}
