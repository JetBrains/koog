package ai.koog.a2a.server.jsonrpc

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.consts.A2AVersions
import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.AgentScript
import ai.koog.a2a.server.FixedIdGenerator
import ai.koog.a2a.server.ScriptedAgentExecutor
import ai.koog.a2a.server.TASK_ID
import ai.koog.a2a.server.TEST_TIMEOUT
import ai.koog.a2a.server.statusUpdate
import ai.koog.a2a.server.task
import ai.koog.a2a.server.testAgentCard
import ai.koog.a2a.server.userMessage
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import ai.koog.a2a.transport.jsonrpc.A2AMethod
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCError
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCErrorResponse
import ai.koog.a2a.transport.jsonrpc.model.JSONRPCRequest
import ai.koog.a2a.transport.jsonrpc.model.JSONRPC_VERSION
import ai.koog.a2a.transport.jsonrpc.model.RequestId
import ai.koog.a2a.transport.jsonrpc.serialization.JSONRPCJson
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.netty.Netty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.encodeToJsonElement
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests of an [A2AServer] served by [HttpJSONRPCServerTransport] on a real HTTP server, checking the JSON-RPC
 * responses on the wire.
 */
class A2AServerJsonRpcTransportIntegrationTest {
    private val testPath = "/a2a"

    private val httpClient = HttpClient(CIO)
    private lateinit var serverTransport: HttpJSONRPCServerTransport
    private lateinit var client: A2AClient
    private lateinit var serverUrl: String

    private fun startServer(scripts: Map<String, AgentScript>): Unit = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val card = testAgentCard(AgentCapabilities(streaming = true))
        serverUrl = "http://localhost:$port$testPath"
        serverTransport = HttpJSONRPCServerTransport(
            A2AServer(agentExecutor = ScriptedAgentExecutor(scripts), agentCard = card, idGenerator = FixedIdGenerator())
        )
        serverTransport.start(engineFactory = Netty, port = port, path = testPath)
        client = A2AClient(card, HttpJSONRPCClientTransport(serverUrl, httpClient))
    }

    @AfterTest
    fun tearDown(): Unit = runBlocking {
        if (::serverTransport.isInitialized) serverTransport.stop()
        httpClient.close()
    }

    /**
     * A follow-up to a task waiting for input, whose agent rejects it with an A2A error before its first event, is
     * answered with that error's JSON-RPC code, for both SendMessage and SendStreamingMessage (as plain JSON, since no
     * stream is opened yet), and leaves the task unchanged.
     */
    @Test
    fun testA2AErrorBeforeFirstEventOfFollowUpIsJsonRpcErrorAndLeavesTaskUnchanged() = runTest(timeout = TEST_TIMEOUT) {
        startServer(
            mapOf(
                "ask" to AgentScript { _, processor ->
                    processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED, "need input"))
                },
                "reject" to AgentScript { _, _ ->
                    throw A2AContentTypeNotSupportedException("Unsupported content type")
                },
            )
        )

        // Real time: the HTTP server and client run on their own threads
        withContext(Dispatchers.Default) {
            client.sendMessage(SendMessageRequest(userMessage("ask")))
            val inputRequiredTask = client.getTask(GetTaskRequest(TASK_ID))
            assertEquals(TaskState.TASK_STATE_INPUT_REQUIRED, inputRequiredTask.status.state)

            listOf(A2AMethod.SendMessage, A2AMethod.SendMessageStreaming).forEach { method ->
                val requestId = RequestId.StringId("follow-up-${method.value}")
                val response = httpClient.post(serverUrl) {
                    contentType(ContentType.Application.Json)
                    header(A2AHeaders.A2A_VERSION, A2AVersions.VERSION_1_0)
                    if (method.streaming) accept(ContentType.Text.EventStream)
                    setBody(
                        JSONRPCJson.encodeToString(
                            JSONRPCRequest(
                                id = requestId,
                                method = method.value,
                                params = JSONRPCJson.encodeToJsonElement(
                                    SendMessageRequest(userMessage("reject", TASK_ID))
                                ),
                                jsonrpc = JSONRPC_VERSION,
                            )
                        )
                    )
                }

                val expectedResponse = JSONRPCErrorResponse(
                    id = requestId,
                    error = JSONRPCError(
                        code = -32005,
                        message = "Unsupported content type",
                        data = JSONRPCJson.encodeToJsonElement<List<ErrorData>>(
                            listOf(ErrorInfo(reason = A2AErrorReasons.CONTENT_TYPE_NOT_SUPPORTED))
                        ),
                    ),
                    jsonrpc = JSONRPC_VERSION,
                )

                assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters(), method.value)
                assertEquals(
                    expectedResponse,
                    JSONRPCJson.decodeFromString<JSONRPCErrorResponse>(response.bodyAsText()),
                    method.value,
                )
                assertEquals(inputRequiredTask, client.getTask(GetTaskRequest(TASK_ID)), method.value)
            }
        }
    }
}
