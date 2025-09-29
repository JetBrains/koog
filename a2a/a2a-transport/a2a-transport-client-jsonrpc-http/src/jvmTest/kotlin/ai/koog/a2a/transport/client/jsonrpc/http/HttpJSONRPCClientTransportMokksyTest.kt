package ai.koog.a2a.transport.client.jsonrpc.http

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TaskQueryParams
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.ClientCallContext
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.RequestId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.MissingFieldException
import me.kpavlov.aimocks.a2a.MockAgentServer
import me.kpavlov.aimocks.a2a.model.Task
import me.kpavlov.aimocks.a2a.model.TaskStatus
import kotlin.test.Test

class HttpJSONRPCClientTransportMokksyTest {
    val a2aServer = MockAgentServer(verbose = true)

    val client = HttpJSONRPCClientTransport(a2aServer.baseUrl(), HttpClient())

    @Test
    fun `Should sendMessage`() = runTest {
        a2aServer.sendMessage() responds {
            id = "req_1234"
            result = Task(
                id = "tid_12345",
                sessionId = "de38c76d-d54c-436c-8b9f-4c2703648d64",
                contextId = "ctx_12345",
                status = TaskStatus("submitted")
            )
        }

        val response = client.sendMessage(
            request = Request(
                id = RequestId.StringId("req_1234"),
                data = MessageSendParams(
                    message = Message(
                        role = Role.User,
                        parts = listOf(
                            TextPart("Tell me a joke")
                        )
                    )
                )
            ),
            ctx = ClientCallContext()
        )

        response shouldNotBeNull {
            id shouldBe RequestId.StringId("req_1234")
            (data as? ai.koog.a2a.model.Task) shouldNotBeNull {
                id shouldBe "tid_12345"
                contextId shouldBe "ctx_12345"
                status shouldBe ai.koog.a2a.model.TaskStatus(TaskState.Submitted)
            }
        }
    }

    @Test
    fun `Should getTask`() = runTest {
        a2aServer.getTask() responds {
            id = 1
            result = Task(
                id = "tid_12345",
                contextId = "ctx_12345",
                sessionId = "de38c76d-d54c-436c-8b9f-4c2703648d64",
                status = TaskStatus("canceled")
            )
        }

        val response = client.getTask(
            request = Request(
                id = RequestId.StringId("req_1234"),
                data = TaskQueryParams(id = "tid_12345")
            ),
            ctx = ClientCallContext()
        )

        response shouldNotBeNull {
            id shouldBe RequestId.NumberId(1)
            data shouldNotBeNull {
                id shouldBe "tid_12345"
                contextId shouldBe "ctx_12345"
                status shouldBe ai.koog.a2a.model.TaskStatus(TaskState.Canceled)
            }
        }
    }

    @Test
    fun `Should handle getTask with missing contextId`() = runTest {
        a2aServer.getTask() responds {
            id = 1
            result {
                id = "tid_12345"
                sessionId = "de38c76d-d54c-436c-8b9f-4c2703648d64"
                status {
                    state = "completed"
                }
                artifacts += artifact {
                    name = "joke"
                    parts += textPart {
                        text = "This is a joke"
                    }
                }
            }
        }

        shouldThrow<MissingFieldException> {
            client.getTask(
                request = Request(
                    id = RequestId.StringId("req_1234"),
                    data = TaskQueryParams(id = "tid_12345")
                ),
                ctx = ClientCallContext()
            )
        }.missingFields shouldBe listOf("contextId")
    }

    @Test
    fun `Should cancelTask`() = runTest {
        a2aServer.cancelTask() responds {
            id = "req_123"
            result = Task(
                id = "tid_12345",
                contextId = "ctx_12345",
                sessionId = "de38c76d-d54c-436c-8b9f-4c2703648d64",
                status = TaskStatus("canceled")
            )
        }

        val response = client.cancelTask(
            request = Request(
                id = RequestId.StringId("req_1233"),
                data = TaskIdParams("tid_12345")
            ),
            ctx = ClientCallContext()
        )

        response shouldNotBeNull {
            id shouldBe RequestId.StringId("req_123")
            data shouldNotBeNull {
                id shouldBe "tid_12345"
                contextId shouldBe "ctx_12345"
                status shouldBe ai.koog.a2a.model.TaskStatus(TaskState.Canceled)
            }
        }
    }
}
