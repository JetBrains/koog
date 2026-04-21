package ai.koog.a2a.client

import ai.koog.a2a.test.BaseA2AProtocolTest
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.test.utils.DockerAvailableCondition
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Integration test class for testing the JSON-RPC HTTP communication in the A2A client context.
 * This class ensures the proper functioning and correctness of the A2A protocol over HTTP
 * using the JSON-RPC standard.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD, reason = "Working with the same instance of test server.")
class A2AClientJsonRpcIntegrationTest : BaseA2AProtocolTest() {
    val testA2AServer = TestA2AServerContainer

    override val testTimeout = 10.seconds

    private val httpClient = HttpClient {
        install(Logging) {
            level = LogLevel.BODY
        }
    }

    @Suppress("HttpUrlsUsage")
    private val agentUrl by lazy { "http://${testA2AServer.host}:${testA2AServer.port}" }

    private lateinit var transport: HttpJSONRPCClientTransport

    override lateinit var client: A2AClient

    @BeforeAll
    fun setUp() = runBlocking {
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
    fun tearDown() {
        transport.close()
        testA2AServer.shutdown()
    }

    @Test
    override fun `test get agent card`() =
        super.`test get agent card`()

    @Test
    override fun `test get authenticated extended agent card`() =
        super.`test get authenticated extended agent card`()

    @Test
    override fun `test send message`() =
        super.`test send message`()

    @Test
    override fun `test send message streaming`() =
        super.`test send message streaming`()

    @Test
    override fun `test get task`() =
        super.`test get task`()

    @Test
    override fun `test cancel task`() =
        super.`test cancel task`()

    @Test
    override fun `test resubscribe task`() =
        super.`test resubscribe task`()

    @Test
    override fun `test push notification configs`() =
        super.`test push notification configs`()
}
