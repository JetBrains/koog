package ai.koog.a2a.server.jsonrpc

import ai.koog.a2a.annotations.InternalA2AApi
import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.consts.A2AHeaders
import ai.koog.a2a.consts.A2AVersions
import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AErrorReasons
import ai.koog.a2a.exceptions.ErrorData
import ai.koog.a2a.exceptions.ErrorInfo
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.AgentScript
import ai.koog.a2a.server.CONTEXT_ID
import ai.koog.a2a.server.FixedIdGenerator
import ai.koog.a2a.server.ScriptedAgentExecutor
import ai.koog.a2a.server.TASK_ID
import ai.koog.a2a.server.TIMESTAMP
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.testAgentCard
import ai.koog.a2a.server.turn.Turn
import ai.koog.a2a.transport.ServerCallContext
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.encodeToJsonElement
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Tests of an [A2AServer] served by [HttpJSONRPCServerTransport] on a real HTTP server, for behavior that depends on
 * the HTTP layer: client disconnects and errors answered before a stream is opened.
 */
@OptIn(InternalA2AApi::class)
class A2AServerJsonRpcTransportIntegrationTest {
    private val testTimeout = 30.seconds
    private val testPath = "/a2a"

    private val httpClient = HttpClient(CIO)
    private lateinit var serverTransport: HttpJSONRPCServerTransport
    private lateinit var server: ObservedServer
    private lateinit var client: A2AClient
    private lateinit var serverUrl: String

    /** Exposes the turn layer and the completion of the server-side SendStreamingMessage flows. */
    private class ObservedServer(agentExecutor: AgentExecutor) : A2AServer(
        agentExecutor = agentExecutor,
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        idGenerator = FixedIdGenerator(),
    ) {
        /** Completed with the cause the first SendStreamingMessage flow ended with (`null` if it ended normally). */
        val streamCompletion = CompletableDeferred<Throwable?>()

        override fun onSendMessageStreaming(request: SendMessageRequest, ctx: ServerCallContext): Flow<Event> =
            super.onSendMessageStreaming(request, ctx).onCompletion { streamCompletion.complete(it) }

        suspend fun turn(taskId: String): Turn? = turnManager.getTurn(taskId)

        suspend fun activeTurns(): Int = turnManager.activeTurns()

        suspend fun activeStreams(): Int = turnManager.activeStreams()
    }

    private fun startServer(scripts: Map<String, AgentScript>): Unit = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        serverUrl = "http://localhost:$port$testPath"
        server = ObservedServer(ScriptedAgentExecutor(scripts))
        serverTransport = HttpJSONRPCServerTransport(server)
        serverTransport.start(engineFactory = Netty, port = port, path = testPath)
        client = A2AClient(testAgentCard(AgentCapabilities(streaming = true)), HttpJSONRPCClientTransport(serverUrl, httpClient))
    }

    @AfterTest
    fun tearDown(): Unit = runBlocking {
        if (::serverTransport.isInitialized) serverTransport.stop()
        httpClient.close()
    }

    private fun userMessage(text: String, taskId: String? = null) = Message(
        messageId = "user-$text",
        role = Role.ROLE_USER,
        parts = listOf(TextPart(text)),
        contextId = CONTEXT_ID,
        taskId = taskId,
    )

    private fun task(state: TaskState, history: List<Message>? = null) = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, timestamp = TIMESTAMP),
        history = history,
    )

    private fun status(state: TaskState, text: String) = TaskStatusUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(
            state = state,
            message = Message(
                messageId = "agent-$text",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart(text)),
                contextId = CONTEXT_ID,
                taskId = TASK_ID,
            ),
            timestamp = TIMESTAMP,
        ),
    )

    /**
     * A streaming client that disconnects mid-stream cancels the server-side flow, which releases the requester's view
     * of the task, so the agent isn't blocked delivering its next events: it keeps running and finishes the task, and
     * a subscriber gets the rest of the events.
     */
    @Test
    fun testSseClientDisconnectCancelsServerFlowAndUnblocksAgent() = runTest(timeout = testTimeout) {
        val resumeAgent = CompletableDeferred<Unit>()
        val agentFinished = CompletableDeferred<Unit>()
        val stepTwo = status(TaskState.TASK_STATE_WORKING, "step 2")
        val completed = status(TaskState.TASK_STATE_COMPLETED, "done")

        startServer(
            mapOf(
                "work" to AgentScript { _, processor ->
                    processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
                    resumeAgent.await()
                    processor.sendTaskEvent(stepTwo)
                    processor.sendTaskEvent(completed)
                    agentFinished.complete(Unit)
                }
            )
        )

        // Real time: the HTTP server and client run on their own threads
        withContext(Dispatchers.Default) {
            val firstEvent = CompletableDeferred<Event>()
            val streamingClient = launch {
                client.sendMessageStreaming(SendMessageRequest(userMessage("work"))).collect {
                    firstEvent.complete(it)
                }
            }

            val workingTask = task(TaskState.TASK_STATE_WORKING, history = listOf(userMessage("work", TASK_ID)))
            assertEquals<Event>(workingTask, firstEvent.await())

            // The agent is waiting on the gate, so nothing is written to the stream after the disconnect
            streamingClient.cancelAndJoin()

            assertIs<CancellationException>(server.streamCompletion.await())

            val turn = assertNotNull(server.turn(TASK_ID))
            assertEquals(1, server.activeTurns())

            val subscribed = CompletableDeferred<Unit>()
            val subscriberEvents = mutableListOf<Event>()
            val subscriber = launch {
                client.subscribeToTask(SubscribeToTaskRequest(TASK_ID)).collect {
                    subscriberEvents += it
                    subscribed.complete(Unit)
                }
            }
            subscribed.await()

            resumeAgent.complete(Unit)

            agentFinished.await()
            subscriber.join()
            turn.join()

            assertEquals(listOf<Event>(workingTask, stepTwo, completed), subscriberEvents)
            assertEquals(
                Task(
                    id = TASK_ID,
                    contextId = CONTEXT_ID,
                    status = completed.status,
                    history = listOf(userMessage("work", TASK_ID), assertNotNull(stepTwo.status.message)),
                ),
                client.getTask(GetTaskRequest(TASK_ID)),
            )
            assertEquals(0, server.activeTurns())
            assertEquals(0, server.activeStreams())
        }
    }

    /**
     * A follow-up to a task waiting for input, whose agent rejects it with an A2A error before its first event, is
     * answered with that error's JSON-RPC code, for both SendMessage and SendStreamingMessage (as plain JSON, since no
     * stream is opened yet), and leaves the task unchanged.
     */
    @Test
    fun testA2AErrorBeforeFirstEventOfFollowUpIsJsonRpcErrorAndLeavesTaskUnchanged() = runTest(timeout = testTimeout) {
        startServer(
            mapOf(
                "ask" to AgentScript { _, processor ->
                    processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
                    processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED, "need input"))
                },
                "reject" to AgentScript { _, _ ->
                    throw A2AContentTypeNotSupportedException("Unsupported content type")
                },
            )
        )

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

    @Test
    fun testExperimentRawSocket() = runTest(timeout = testTimeout) {
        val resumeAgent = CompletableDeferred<Unit>()
        val agentFinished = CompletableDeferred<Unit>()
        val gate2 = CompletableDeferred<Unit>()
        startServer(
            mapOf(
                "work" to AgentScript { _, processor ->
                    processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
                    resumeAgent.await()
                    System.err.println("DBG agent: sending step 2")
                    processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING, "step 2"))
                    System.err.println("DBG agent: step 2 sent")
                    gate2.await()
                    System.err.println("DBG agent: sending completed")
                    processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED, "done"))
                    System.err.println("DBG agent: finished")
                    agentFinished.complete(Unit)
                }
            )
        )
        withContext(Dispatchers.IO) {
            val body = JSONRPCJson.encodeToString(
                JSONRPCRequest(
                    id = RequestId.StringId("1"),
                    method = A2AMethod.SendMessageStreaming.value,
                    params = JSONRPCJson.encodeToJsonElement(SendMessageRequest(userMessage("work"))),
                    jsonrpc = JSONRPC_VERSION,
                )
            ).toByteArray()
            val uri = java.net.URI(serverUrl)
            val socket = java.net.Socket("localhost", uri.port)
            val out = socket.getOutputStream()
            out.write(("POST $testPath HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\nAccept: text/event-stream\r\nA2A-Version: 1.0\r\nContent-Length: ${body.size}\r\n\r\n").toByteArray())
            out.write(body)
            out.flush()
            val reader = socket.getInputStream().bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                System.err.println("DBG read: $line")
                if (line.contains("data:")) break
            }
            socket.setSoLinger(true, 0); socket.close()
            System.err.println("DBG socket closed")
            val c = kotlinx.coroutines.withTimeoutOrNull(5000) { Result.success(server.streamCompletion.await()) }
            System.err.println("DBG completion within 5s without write: $c")
            resumeAgent.complete(Unit)
            val c2 = kotlinx.coroutines.withTimeoutOrNull(5000) { Result.success(server.streamCompletion.await()) }
            System.err.println("DBG completion after one write: $c2")
            gate2.complete(Unit)
            val f = kotlinx.coroutines.withTimeoutOrNull(5000) { agentFinished.await() }
            System.err.println("DBG agent finished: $f")
            val c3 = kotlinx.coroutines.withTimeoutOrNull(5000) { Result.success(server.streamCompletion.await()) }
            System.err.println("DBG completion after two writes: $c3")
        }
    }
}
