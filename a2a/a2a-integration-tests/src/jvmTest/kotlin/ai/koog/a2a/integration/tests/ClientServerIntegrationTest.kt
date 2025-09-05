package ai.koog.a2a.integration.tests

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestHandler
import ai.koog.a2a.transport.RequestId
import ai.koog.a2a.transport.Response
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServer
import io.ktor.server.cio.CIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import kotlin.random.Random.Default.nextInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ClientServerIntegrationTest {

    lateinit var taskId: TaskIdParams
    lateinit var requestId: RequestId

    val handler = object : RequestHandler {
        override suspend fun onListTaskPushNotificationConfig(
            request: Request<TaskIdParams>,
            ctx: ServerCallContext
        ): Response<List<TaskPushNotificationConfig>> {
            delay(150.milliseconds)
            return Response(
                id = request.id,
                data = listOf(
                    TaskPushNotificationConfig(
                        taskId = taskId.id,
                        pushNotificationConfig = PushNotificationConfig(
                            "conf_123",
                            url = "https://example.com",
                            token = null,
                            authentication = null
                        )
                    )
                )
            )
        }
    }

    val server = HttpJSONRPCServer(CIO, handler = handler).apply {
        start()
    }

    val client = A2AClient(HttpJSONRPCClientTransport("http://localhost:${server.port}/a2a"))

    @BeforeEach
    fun beforeEach() {
        taskId = TaskIdParams("tid_${nextInt(1000, 9999)}")
        requestId = RequestId.StringId("req_${nextInt(1000, 9999)}")
    }

    @AfterAll
    fun afterAll() {
        server.stop()
    }

    @Test
    fun `Connectivity test`() = runTest {
        val response = client.listTaskPushNotificationConfig(
            request = Request(
                id = requestId,
                data = taskId
            ),
            ctx = ClientCallContext()
        )

        assertTrue { response.data.size == 1 }
        response.data.first().let {
            assertEquals(expected = taskId.id, actual = it.taskId)
            assertEquals(expected = "https://example.com", actual = it.pushNotificationConfig.url)
        }
    }
}
