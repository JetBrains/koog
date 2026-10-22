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
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.AgentScript
import ai.koog.a2a.server.FixedIdGenerator
import ai.koog.a2a.server.ScriptedAgentExecutor
import ai.koog.a2a.server.TASK_ID
import ai.koog.a2a.server.TEST_TIMEOUT
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.statusUpdate
import ai.koog.a2a.server.task
import ai.koog.a2a.server.testAgentCard
import ai.koog.a2a.server.userMessage
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
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.encodeToJsonElement
import java.io.BufferedReader
import java.io.Closeable
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Tests of an [A2AServer] served by [HttpJSONRPCServerTransport] on a real HTTP server, checking the JSON-RPC
 * responses and the SSE streams on the wire.
 */
@OptIn(InternalA2AApi::class)
class A2AServerJsonRpcTransportIntegrationTest {
    private val testPath = "/a2a"
    private val heartbeatPeriod = 50.milliseconds

    private val httpClient = HttpClient(CIO)
    private lateinit var serverTransport: HttpJSONRPCServerTransport
    private lateinit var server: ObservedServer
    private lateinit var client: A2AClient
    private lateinit var serverUrl: String

    /** Exposes the stream registry and the completion of the server-side SubscribeToTask flow. */
    private class ObservedServer(agentExecutor: AgentExecutor) : A2AServer(
        agentExecutor = agentExecutor,
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        idGenerator = FixedIdGenerator(),
    ) {
        /** Completed when the first SubscribeToTask flow ends, however it ends. */
        val subscriptionCompletion = CompletableDeferred<Unit>()

        override fun onSubscribeToTask(request: SubscribeToTaskRequest, ctx: ServerCallContext): Flow<Event> =
            super.onSubscribeToTask(request, ctx).onCompletion { subscriptionCompletion.complete(Unit) }

        suspend fun activeStreams(): Int = turnManager.activeStreams()
    }

    private fun <TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration> startServer(
        engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
        scripts: Map<String, AgentScript>,
        heartbeatPeriod: Duration? = this.heartbeatPeriod,
    ): Unit = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val card = testAgentCard(AgentCapabilities(streaming = true))
        serverUrl = "http://localhost:$port$testPath"
        server = ObservedServer(ScriptedAgentExecutor(scripts))
        serverTransport = HttpJSONRPCServerTransport(server)
        serverTransport.start(
            engineFactory = engineFactory,
            port = port,
            path = testPath,
            heartbeatPeriod = heartbeatPeriod,
        )
        client = A2AClient(card, HttpJSONRPCClientTransport(serverUrl, httpClient))
    }

    /** Scripts of a task that waits for input after "ask" and completes after "finish" once [finishGate] is open. */
    private fun inputRequiredScripts(finishGate: CompletableDeferred<Unit> = CompletableDeferred(Unit)) = mapOf(
        "ask" to AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED, "need input"))
        },
        "finish" to AgentScript { _, processor ->
            finishGate.await()
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED, "done"))
        },
    )

    /** Opens a SubscribeToTask stream with a raw socket and reads the response until its first event. */
    private fun openSubscription(): SocketStream {
        val body = JSONRPCJson.encodeToString(
            JSONRPCRequest(
                id = RequestId.StringId("subscribe"),
                method = A2AMethod.SubscribeToTask.value,
                params = JSONRPCJson.encodeToJsonElement(SubscribeToTaskRequest(TASK_ID)),
                jsonrpc = JSONRPC_VERSION,
            )
        ).toByteArray()
        // Blocking reads aren't cancellable, so bound them
        val socket = Socket("localhost", URI(serverUrl).port).apply { soTimeout = DISCONNECT_TIMEOUT.inWholeMilliseconds.toInt() }
        socket.getOutputStream().apply {
            write(
                (
                    "POST $testPath HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" +
                        "Accept: text/event-stream\r\n${A2AHeaders.A2A_VERSION}: ${A2AVersions.VERSION_1_0}\r\n" +
                        "Content-Length: ${body.size}\r\n\r\n"
                    ).toByteArray()
            )
            write(body)
            flush()
        }
        return SocketStream(socket, socket.getInputStream().bufferedReader()).also { stream ->
            while (!stream.readLine().startsWith("data:")) Unit
        }
    }

    /** A raw HTTP connection whose response lines are read directly, to observe the stream bytes. */
    private class SocketStream(val socket: Socket, private val reader: BufferedReader) : Closeable {
        fun readLine(): String = reader.readLine() ?: error("The server closed the stream")

        /**
         * Closes with a FIN: reads everything already received first, because closing a socket that has unread data
         * sends a reset instead, which Ktor's Netty engine doesn't handle.
         */
        fun closeAfterDraining() {
            socket.soTimeout = DRAIN_TIMEOUT.inWholeMilliseconds.toInt()
            try {
                while (reader.readLine() != null) Unit
            } catch (_: SocketTimeoutException) {
                // Nothing left to read
            }
            close()
        }

        override fun close() = socket.close()
    }

    @AfterTest
    fun tearDown(): Unit = runBlocking {
        if (::serverTransport.isInitialized) serverTransport.stop()
        httpClient.close()
    }

    private suspend fun assertSubscriberReleasedAfterClientCloses() {
        // Real time: the HTTP server runs on its own threads
        withContext(Dispatchers.Default) {
            client.sendMessage(SendMessageRequest(userMessage("ask")))
            val streamsBeforeSubscribe = server.activeStreams()

            val stream = openSubscription()
            assertEquals(streamsBeforeSubscribe + 1, server.activeStreams())

            // A normal close (FIN): the server only notices when a write to the socket fails
            stream.closeAfterDraining()

            val completion = withTimeoutOrNull(DISCONNECT_TIMEOUT) { server.subscriptionCompletion.await() }
            assertNotNull(completion, "The subscription flow is still running")
            withTimeout(DISCONNECT_TIMEOUT) {
                while (server.activeStreams() != streamsBeforeSubscribe) delay(10.milliseconds)
            }
        }
    }

    /**
     * A SubscribeToTask stream on a task waiting for input, whose client closes the connection normally, is released
     * by the heartbeat writes failing, although the task produces no events. Netty.
     */
    @Test
    fun testHeartbeatReleasesIdleSubscriptionOfClosedClientOnNetty() = runTest(timeout = TEST_TIMEOUT) {
        startServer(Netty, inputRequiredScripts())
        assertSubscriberReleasedAfterClientCloses()
    }

    /** Same as [testHeartbeatReleasesIdleSubscriptionOfClosedClientOnNetty], on CIO, which needs more writes. */
    @Test
    fun testHeartbeatReleasesIdleSubscriptionOfClosedClientOnCio() = runTest(timeout = TEST_TIMEOUT) {
        startServer(ServerCIO, inputRequiredScripts())
        assertSubscriberReleasedAfterClientCloses()
    }

    /** The server sends SSE comment heartbeats on an idle stream, and none with `heartbeatPeriod = null`. */
    @Test
    fun testHeartbeatIsSentOnlyWhenEnabled() = runTest(timeout = TEST_TIMEOUT) {
        withContext(Dispatchers.Default) {
            startServer(Netty, inputRequiredScripts())
            client.sendMessage(SendMessageRequest(userMessage("ask")))
            openSubscription().use { stream ->
                while (!stream.readLine().contains("heartbeat")) Unit
            }
            serverTransport.stop()

            startServer(Netty, inputRequiredScripts(), heartbeatPeriod = null)
            client.sendMessage(SendMessageRequest(userMessage("ask")))
            openSubscription().use { stream ->
                stream.socket.soTimeout = (heartbeatPeriod * 20).inWholeMilliseconds.toInt()
                val lines = mutableListOf<String>()
                assertFailsWith<SocketTimeoutException> { while (true) lines += stream.readLine() }
                assertTrue(lines.none { it.contains("heartbeat") }, "Unexpected heartbeat in $lines")
            }
        }
    }

    /**
     * The Koog client ignores the heartbeat comments of a long idle stream: the subscriber gets the task snapshot
     * and then the events produced after several heartbeat periods.
     */
    @Test
    fun testClientSubscriptionIgnoresHeartbeats() = runTest(timeout = TEST_TIMEOUT) {
        val finishGate = CompletableDeferred<Unit>()
        startServer(Netty, inputRequiredScripts(finishGate))

        withContext(Dispatchers.Default) {
            client.sendMessage(SendMessageRequest(userMessage("ask")))
            val inputRequiredTask = client.getTask(GetTaskRequest(TASK_ID))

            val received = Channel<Event>(Channel.UNLIMITED)
            val subscriber = launch {
                client.subscribeToTask(SubscribeToTaskRequest(TASK_ID)).collect { received.send(it) }
                received.close()
            }
            assertEquals<Event>(inputRequiredTask, received.receive())

            delay(heartbeatPeriod * 10)
            val followUp = launch { client.sendMessage(SendMessageRequest(userMessage("finish", TASK_ID))) }
            finishGate.complete(Unit)
            followUp.join()
            subscriber.join()

            assertEquals<List<Event>>(listOf(statusUpdate(TaskState.TASK_STATE_COMPLETED, "done")), received.consumeAsFlow().toList())
        }
    }

    private companion object {
        val DISCONNECT_TIMEOUT = 10.seconds
        val DRAIN_TIMEOUT = 20.milliseconds
    }

    /**
     * A follow-up to a task waiting for input, whose agent rejects it with an A2A error before its first event, is
     * answered with that error's JSON-RPC code, for both SendMessage and SendStreamingMessage (as plain JSON, since no
     * stream is opened yet), and leaves the task unchanged.
     */
    @Test
    fun testA2AErrorBeforeFirstEventOfFollowUpIsJsonRpcErrorAndLeavesTaskUnchanged() = runTest(timeout = TEST_TIMEOUT) {
        startServer(
            Netty,
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
