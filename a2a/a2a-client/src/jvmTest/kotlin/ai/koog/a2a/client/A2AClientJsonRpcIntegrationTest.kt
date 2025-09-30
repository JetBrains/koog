package ai.koog.a2a.client

import ai.koog.a2a.test.BaseA2AProtocolTest
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.time.Duration.Companion.minutes

/**
 * Integration test class for testing the JSON-RPC HTTP communication in the A2A client context.
 * This class ensures the proper functioning and correctness of the A2A protocol over HTTP
 * using the JSON-RPC standard.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Testcontainers
@EnabledOnOs(OS.LINUX)
class A2AClientJsonRpcIntegrationTest : BaseA2AProtocolTest() {
    companion object {
        @Container
        val testA2AServer: GenericContainer<*> =
            GenericContainer("test-python-a2a-server")
                .withExposedPorts(9999)
                .waitingFor(Wait.forListeningPort())
    }

    override val testTimeout = 2.minutes

    private val httpClient = HttpClient {
        install(Logging) {
            level = LogLevel.BODY
        }
    }

    @Suppress("HttpUrlsUsage")
    private val agentUrl by lazy { "http://${testA2AServer.host}:${testA2AServer.getMappedPort(9999)}" }

    private lateinit var transport: HttpJSONRPCClientTransport

    override lateinit var client: A2AClient

    @BeforeAll
    fun setUp() = runTest {
        transport = HttpJSONRPCClientTransport(
            url = agentUrl,
            baseHttpClient = httpClient
        )

        client = A2AClient(
            transport = transport,
            agentCardResolver = UrlAgentCardResolver(
                baseUrl = agentUrl,
                baseHttpClient = httpClient,
            ),
        )

        client.connect()
    }

    @AfterAll
    fun tearDown() = runTest {
        transport.close()
    }
}
