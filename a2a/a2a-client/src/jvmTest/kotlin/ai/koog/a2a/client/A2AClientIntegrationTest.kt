package ai.koog.a2a.client

import ai.koog.a2a.test.BaseA2AProtocolTest
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.test.runTest
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.BeforeTest

@Testcontainers
class A2AClientIntegrationTest : BaseA2AProtocolTest() {
    companion object {
        @Container
        val testA2AServer: GenericContainer<*> =
            GenericContainer("test-python-a2a-server")
                .withExposedPorts(9999)
                .waitingFor(Wait.forListeningPort())
    }

    private val httpClient = HttpClient {
        install(Logging) {
            level = LogLevel.BODY
        }
    }

    @Suppress("HttpUrlsUsage")
    private val agentUrl by lazy { "http://${testA2AServer.host}:${testA2AServer.getMappedPort(9999)}" }

    private val transport by lazy {
        HttpJSONRPCClientTransport(
            url = agentUrl,
            baseHttpClient = httpClient
        )
    }

    override val client by lazy {
        A2AClient(
            transport = transport,
            agentCardResolver = UrlAgentCardResolver(
                baseUrl = agentUrl,
                baseHttpClient = httpClient,
            ),
        )
    }

    @BeforeTest
    fun initClient() = runTest {
        client.connect()
    }
}
