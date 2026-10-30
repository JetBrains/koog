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
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.server.AgentScript
import ai.koog.a2a.server.CONTEXT_ID
import ai.koog.a2a.server.FixedIdGenerator
import ai.koog.a2a.server.ScriptedAgentExecutor
import ai.koog.a2a.server.StreamDeliveryConfig
import ai.koog.a2a.server.TASK_ID
import ai.koog.a2a.server.TEST_TIMEOUT
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.statusUpdate
import ai.koog.a2a.server.task
import ai.koog.a2a.server.testAgentCard
import ai.koog.a2a.server.userMessage
import ai.koog.a2a.transport.A2AMethod
import ai.koog.a2a.transport.ServerCallContext
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
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
import java.io.InputStream
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
import kotlin.time.TimeSource
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
    private class ObservedServer(agentExecutor: AgentExecutor, streamDelivery: StreamDeliveryConfig) : A2AServer(
        agentExecutor = agentExecutor,
        agentCard = testAgentCard(AgentCapabilities(streaming = true)),
        idGenerator = FixedIdGenerator(),
        streamDelivery = streamDelivery,
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
        streamDelivery: StreamDeliveryConfig = StreamDeliveryConfig.Evict(),
    ): Unit = runBlocking {
        val port = ServerSocket(0).use { it.localPort }
        val card = testAgentCard(AgentCapabilities(streaming = true))
        serverUrl = "http://localhost:$port$testPath"
        server = ObservedServer(ScriptedAgentExecutor(scripts), streamDelivery)
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

    /**
     * Opens a SubscribeToTask stream with a raw socket, like [openSubscription], but decodes the chunked body, so that
     * large events split across chunks are read whole. Returns the reader of the SSE lines after the first event.
     */
    private fun openDechunkedSubscription(socket: Socket): BufferedReader {
        val body = JSONRPCJson.encodeToString(
            JSONRPCRequest(
                id = RequestId.StringId("subscribe"),
                method = A2AMethod.SubscribeToTask.value,
                params = JSONRPCJson.encodeToJsonElement(SubscribeToTaskRequest(TASK_ID)),
                jsonrpc = JSONRPC_VERSION,
            )
        ).toByteArray()
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

        val input = socket.getInputStream().buffered()
        val headers = generateSequence { input.readAsciiLine() }.takeWhile { it.isNotEmpty() }.toList()
        assertTrue(headers.any { it.equals("Transfer-Encoding: chunked", ignoreCase = true) }, "Not chunked: $headers")

        return ChunkedInputStream(input).bufferedReader().also { reader ->
            while (reader.readLine()?.startsWith("data:") == false) Unit
        }
    }

    /** Decodes an HTTP/1.1 chunked body. */
    private class ChunkedInputStream(private val input: InputStream) : InputStream() {
        private var remaining = 0
        private var isEnd = false

        override fun read(): Int {
            val buffer = ByteArray(1)
            return if (read(buffer, 0, 1) < 0) -1 else buffer[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (isEnd) return -1
            if (remaining == 0) {
                val size = input.readAsciiLine()?.substringBefore(';')?.trim()?.toInt(16) ?: return -1
                if (size == 0) {
                    isEnd = true
                    return -1
                }
                remaining = size
            }

            val read = input.read(buffer, offset, minOf(length, remaining))
            if (read < 0) return -1
            remaining -= read
            // The CRLF after the chunk data.
            if (remaining == 0) input.readAsciiLine()
            return read
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

        /** Reads a line terminated by CRLF or LF, without it; `null` at the end of the stream. */
        fun InputStream.readAsciiLine(): String? {
            val line = StringBuilder()
            while (true) {
                when (val byte = read()) {
                    -1 -> return line.takeIf { it.isNotEmpty() }?.toString()
                    '\n'.code -> return line.toString().removeSuffix("\r")
                    else -> line.append(byte.toChar())
                }
            }
        }
    }

    /**
     * A SubscribeToTask client that stops reading while the agent sends more than the kernel, Ktor, and the stream's
     * queue can hold is evicted, without holding up the agent beyond the timeout: once it reads again, it gets the
     * events sent before, in order, and then a final `-32603` error frame with the `SLOW_CONSUMER` reason. Netty.
     */
    @Test
    fun testStalledSseSubscriberIsEvictedWithFinalErrorFrame() = runTest(timeout = 60.seconds) {
        val eventCount = 200
        // Large events, so the socket and Ktor buffers fill up after a few of them.
        val padding = "x".repeat(256 * 1024)
        val evictTimeout = 2.seconds
        startServer(
            Netty,
            mapOf(
                "ask" to AgentScript { _, processor ->
                    processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING))
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED, "need input"))
                },
                "flood" to AgentScript { _, processor ->
                    repeat(eventCount) { index ->
                        processor.sendTaskEvent(
                            TaskArtifactUpdateEvent(
                                taskId = TASK_ID,
                                contextId = CONTEXT_ID,
                                // Replaced every time, so the stored task stays small.
                                artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("event-$index:$padding"))),
                            )
                        )
                    }
                    processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED, "done"))
                },
            ),
            heartbeatPeriod = null,
            streamDelivery = StreamDeliveryConfig.Evict(capacity = 4, timeout = evictTimeout),
        )

        // Real time: the HTTP server and client run on their own threads
        withContext(Dispatchers.Default) {
            client.sendMessage(SendMessageRequest(userMessage("ask")))

            Socket("localhost", URI(serverUrl).port).use { socket ->
                socket.soTimeout = 30.seconds.inWholeMilliseconds.toInt()
                val reader = openDechunkedSubscription(socket)

                // The subscriber reads nothing more while the agent floods the task; the agent isn't held up for long.
                val start = TimeSource.Monotonic.markNow()
                val response = client.sendMessage(SendMessageRequest(userMessage("flood", TASK_ID)))
                assertEquals(TaskState.TASK_STATE_COMPLETED, (response as Task).status.state)
                assertTrue(start.elapsedNow() < evictTimeout * 5, "The agent was held up for ${start.elapsedNow()}")

                val frames = generateSequence { reader.readLine() }
                    .filter { it.startsWith("data:") }
                    .map { it.removePrefix("data:").trim() }
                    .toList()

                val eventIndices = frames.dropLast(1).map { frame ->
                    assertNotNull(Regex("\"text\":\"event-(\\d+):").find(frame), "Not an artifact update").groupValues[1].toInt()
                }
                assertEquals(eventIndices.indices.toList(), eventIndices, "The events received before the eviction")
                assertTrue(eventIndices.size < eventCount, "Not evicted: received all $eventCount events")

                val expectedError = JSONRPCErrorResponse(
                    id = RequestId.StringId("subscribe"),
                    error = JSONRPCError(
                        code = -32603,
                        message = "Stream closed because the client is not reading events fast enough; resubscribe " +
                            "with SubscribeToTask or call GetTask",
                        data = JSONRPCJson.encodeToJsonElement<List<ErrorData>>(
                            listOf(ErrorInfo(reason = "SLOW_CONSUMER", metadata = mapOf("taskId" to TASK_ID)))
                        ),
                    ),
                    jsonrpc = JSONRPC_VERSION,
                )
                assertEquals(expectedError, JSONRPCJson.decodeFromString<JSONRPCErrorResponse>(frames.last()))
            }
        }
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
