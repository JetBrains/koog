package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.utils.io.use
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LangfuseExporterHeadersTest {

    @Test
    fun testLangfuseExporterSendsIngestionVersionHeader() {
        val received = Collections.synchronizedList(mutableListOf<Map<String, List<String>>>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received.add(exchange.requestHeaders.entries.associate { it.key to it.value.toList() })
            exchange.requestBody.readBytes()
            val body = "{}".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val port = server.address.port

            val executor = getMockExecutor {
                mockLLMAnswer("The weather in Paris is rainy and overcast.").asDefaultResponse
            }

            // runBlocking, not runTest: the batch span processor exports on real dispatchers, so a virtual clock would race the export.
            runBlocking {
                val agent = AIAgent(
                    promptExecutor = executor,
                    llmModel = OpenAIModels.Chat.GPT4o,
                    systemPrompt = "You are a weather assistant. Answer concisely.",
                ) {
                    install(OpenTelemetry) {
                        addLangfuseExporter(
                            langfuseUrl = "http://127.0.0.1:$port",
                            langfusePublicKey = "pk-test",
                            langfuseSecretKey = "sk-test",
                        )
                    }
                }

                agent.use { runningAgent ->
                    runningAgent.run("What's the weather in Paris?")
                }
            }

            val deadline = System.currentTimeMillis() + 30_000
            while (received.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(100)
            }
            assertTrue(received.isNotEmpty(), "exporter never sent a request")

            val headers = received.first()
            val key = headers.keys.single { it.equals("x-langfuse-ingestion-version", ignoreCase = true) }
            assertEquals(listOf("4"), headers[key])
            assertTrue(headers.keys.any { it.equals("Authorization", ignoreCase = true) })
        } finally {
            server.stop(0)
        }
    }
}
