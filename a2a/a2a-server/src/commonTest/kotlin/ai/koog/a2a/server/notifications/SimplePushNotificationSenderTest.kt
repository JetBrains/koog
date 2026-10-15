package ai.koog.a2a.server.notifications

import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.model.AuthenticationInfo
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SimplePushNotificationSenderTest {
    private val task = Task(
        id = "task-1",
        contextId = "context-1",
        status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED),
    )
    private val config = TaskPushNotificationConfig(
        url = "https://example.com/webhook",
        taskId = "task-1",
        id = "config-1",
    )

    private class Recorder {
        val requests = mutableListOf<HttpRequestData>()
        val bodies = mutableListOf<String>()
    }

    private fun senderWith(
        recorder: Recorder,
        status: HttpStatusCode = HttpStatusCode.OK,
        timeout: Duration = 15.seconds,
    ) = SimplePushNotificationSender(
        baseHttpClient = HttpClient(
            MockEngine { request ->
                recorder.requests += request
                recorder.bodies += (request.body as TextContent).text
                respond("", status)
            }
        ),
        timeout = timeout,
    )

    @Test
    fun testPayloadIsStreamResponseJson() = runTest {
        val recorder = Recorder()

        senderWith(recorder).send(config, task)

        val request = recorder.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://example.com/webhook", request.url.toString())
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        assertEquals(
            Json.parseToJsonElement(
                """{"task": {"id": "task-1", "contextId": "context-1", "status": {"state": "TASK_STATE_COMPLETED"}}}"""
            ),
            Json.parseToJsonElement(recorder.bodies.single())
        )
    }

    @Test
    fun testStatusUpdateEventPayloadIsWrappedAsStatusUpdate() = runTest {
        val recorder = Recorder()
        val event = TaskStatusUpdateEvent(
            taskId = "task-1",
            contextId = "context-1",
            status = TaskStatus(state = TaskState.TASK_STATE_WORKING),
        )

        senderWith(recorder).send(config, event)

        assertEquals(
            Json.parseToJsonElement(
                """{"statusUpdate": {"taskId": "task-1", "contextId": "context-1", "status": {"state": "TASK_STATE_WORKING"}}}"""
            ),
            Json.parseToJsonElement(recorder.bodies.single())
        )
    }

    @Test
    fun testAuthorizationAndNotificationTokenHeaders() = runTest {
        val recorder = Recorder()
        val withAuthAndToken = config.copy(
            authentication = AuthenticationInfo(scheme = "Bearer", credentials = "secret"),
            token = "push-token",
        )
        val withoutCredentials = config.copy(authentication = AuthenticationInfo(scheme = "Bearer"))

        senderWith(recorder).send(withAuthAndToken, task)
        senderWith(recorder).send(withoutCredentials, task)

        val (withHeaders, withoutHeaders) = recorder.requests.map {
            it.headers[HttpHeaders.Authorization] to it.headers[A2AHeaders.X_A2A_NOTIFICATION_TOKEN]
        }
        assertEquals("Bearer secret" to "push-token", withHeaders)
        assertEquals(null to null, withoutHeaders)
    }

    @Test
    fun testDeliveryFailuresAreSwallowed() = runTest {
        val recorder = Recorder()

        senderWith(recorder, status = HttpStatusCode.InternalServerError).send(config, task)
        SimplePushNotificationSender(HttpClient(MockEngine { throw IllegalStateException("connection refused") }))
            .send(config, task)

        assertEquals(1, recorder.requests.size)
    }

    @Test
    fun testSlowWebhookTimesOutAndIsSwallowed() = runTest {
        val neverCompletes = CompletableDeferred<Unit>()
        val sender = SimplePushNotificationSender(
            baseHttpClient = HttpClient(
                MockEngine {
                    neverCompletes.await()
                    respondError(HttpStatusCode.OK)
                }
            ),
            timeout = 50.milliseconds,
        )

        // The request timeout of the Ktor client runs in real time.
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                sender.send(config, task)
            }
        }
    }
}
