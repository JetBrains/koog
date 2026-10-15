package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.server.session.IdGenerator
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * SendMessage and SendStreamingMessage semantics at the [A2AServer] level: blocking and `returnImmediately`, when
 * streams end and what they begin with, and the rules for messages referencing a task. Uses a scripted agent gated by
 * [CompletableDeferred]s.
 */
class A2AServerSendMessageTest {
    private companion object {
        private const val TASK_ID = "task-1"
        private const val CONTEXT_ID = "context-1"
        private const val GENERATED_CONTEXT_ID = "generated-context"
        private val TIMESTAMP = Instant.parse("2025-01-01T10:00:00Z")
        private val TEST_TIMEOUT = 30.seconds
    }

    private val ctx = ServerCallContext()
    private lateinit var taskStorage: InMemoryTaskStorage

    /** Released by the test to let a gated agent go on. */
    private lateinit var gate: CompletableDeferred<Unit>

    /** Completed when a gated agent has returned from `execute`. */
    private lateinit var agentFinished: CompletableDeferred<Unit>

    /** Request contexts of the turns the agent has executed, in order. */
    private val executed = mutableListOf<RequestContext<SendMessageRequest>>()

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        gate = CompletableDeferred()
        agentFinished = CompletableDeferred()
        executed.clear()
    }

    private fun interface Script {
        suspend fun run(context: RequestContext<SendMessageRequest>, processor: SessionEventProcessor)
    }

    /**
     * Runs the script registered for the text of the incoming message.
     */
    private inner class ScriptedAgentExecutor(private val scripts: Map<String, Script>) : AgentExecutor {
        override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: SessionEventProcessor) {
            executed += context
            val text = context.params.message.parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }
            val script = scripts[text] ?: error("No script for '$text'")
            script.run(context, eventProcessor)
        }
    }

    private object FixedIdGenerator : IdGenerator {
        override suspend fun generateContextId(message: Message): String = GENERATED_CONTEXT_ID
        override suspend fun generateTaskId(message: Message): String = TASK_ID
    }

    private val agentCard = AgentCard(
        name = "Test agent",
        description = "Test agent",
        supportedInterfaces = listOf(
            AgentInterface(
                url = "http://localhost/",
                protocolBinding = TransportProtocol.JSONRPC,
                protocolVersion = "1.0"
            )
        ),
        version = "1.0.0",
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = emptyList(),
        capabilities = AgentCapabilities(streaming = true),
    )

    /**
     * Scope for agent jobs: a supervisor, so that a failing agent doesn't fail the test.
     */
    private fun TestScope.agentScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))

    private fun TestScope.createServer(scripts: Map<String, Script>): A2AServer = A2AServer(
        agentExecutor = ScriptedAgentExecutor(scripts),
        agentCard = agentCard,
        taskStorage = taskStorage,
        idGenerator = FixedIdGenerator,
        coroutineScope = agentScope(),
    )

    private fun userMessage(text: String, taskId: String? = null, contextId: String? = CONTEXT_ID) = Message(
        messageId = "user-$text",
        role = Role.ROLE_USER,
        parts = listOf(TextPart(text)),
        contextId = contextId,
        taskId = taskId,
    )

    private fun request(
        text: String,
        taskId: String? = null,
        contextId: String? = CONTEXT_ID,
        returnImmediately: Boolean? = null,
        historyLength: Int? = null,
    ) = SendMessageRequest(
        message = userMessage(text, taskId, contextId),
        configuration = if (returnImmediately != null || historyLength != null) {
            SendMessageConfiguration(returnImmediately = returnImmediately, historyLength = historyLength)
        } else {
            null
        },
    )

    private fun task(state: TaskState, userText: String) = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, timestamp = TIMESTAMP),
        history = listOf(userMessage(userText)),
    )

    private fun agentMessage(text: String) = Message(
        messageId = "agent-$text",
        role = Role.ROLE_AGENT,
        parts = listOf(TextPart(text)),
        contextId = CONTEXT_ID,
        taskId = TASK_ID,
    )

    private fun status(state: TaskState) = TaskStatusUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, message = agentMessage(state.name), timestamp = TIMESTAMP),
    )

    private fun artifactChunk(text: String) = TaskArtifactUpdateEvent(
        taskId = TASK_ID,
        contextId = CONTEXT_ID,
        artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart(text))),
        append = true,
    )

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val askScript = Script { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "ask"))
        processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    /** The task stored after [askScript]. */
    private val inputRequiredTask = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = status(TaskState.TASK_STATE_INPUT_REQUIRED).status,
        history = listOf(userMessage("ask")),
    )

    /** Follow-up turn that completes the task. */
    private val finishScript = Script { _, processor ->
        processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(artifactChunk("result"))
        processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
    }

    private val finishEvents = listOf(
        status(TaskState.TASK_STATE_WORKING),
        artifactChunk("result"),
        status(TaskState.TASK_STATE_COMPLETED),
    )

    /** Runs [script], then waits for [gate] before returning. */
    private fun gated(script: Script) = Script { context, processor ->
        script.run(context, processor)
        gate.await()
        agentFinished.complete(Unit)
    }

    private suspend fun Flow<Event>.collectExpectingFailure(): Pair<Throwable, List<Event>> {
        val received = mutableListOf<Event>()
        val failure = runCatching { collect { received += it } }.exceptionOrNull()
        return assertNotNull(failure, "Collection was expected to fail") to received
    }

    // Blocking SendMessage and returnImmediately

    @Test
    fun testBlockingReturnsAtInputRequiredWhileAgentStillRunning() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to gated(askScript)))

        val response = server.onSendMessage(request("ask"), ctx)

        assertEquals(inputRequiredTask, response)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testBlockingReturnsAtCompletedWithArtifacts() = runTest(timeout = TEST_TIMEOUT) {
        val completeScript = Script { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "complete"))
            processor.sendTaskEvent(artifactChunk("part-1"))
            processor.sendTaskEvent(artifactChunk("part-2"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("complete" to gated(completeScript)))

        val response = server.onSendMessage(request("complete"), ctx)

        val expected = Task(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            status = status(TaskState.TASK_STATE_COMPLETED).status,
            history = listOf(userMessage("complete")),
            artifacts = listOf(
                Artifact(artifactId = "artifact-1", parts = listOf(TextPart("part-1"), TextPart("part-2")))
            ),
        )
        assertEquals(expected, response)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testBlockingWithoutStopStateReturnsStoredTaskAtEndOfTurn() = runTest(timeout = TEST_TIMEOUT) {
        val workingScript = Script { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "work"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
        }
        val server = createServer(mapOf("work" to workingScript))

        val response = server.onSendMessage(request("work"), ctx)

        val expected = Task(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            status = status(TaskState.TASK_STATE_WORKING).status,
            history = listOf(userMessage("work")),
        )
        assertEquals(expected, response)
    }

    @Test
    fun testReturnImmediatelyReturnsWhileAgentGated() = runTest(timeout = TEST_TIMEOUT) {
        val longScript = Script { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "long"))
            gate.await()
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
            agentFinished.complete(Unit)
        }
        val server = createServer(mapOf("long" to longScript))

        val response = server.onSendMessage(request("long", returnImmediately = true), ctx)

        assertEquals(task(TaskState.TASK_STATE_SUBMITTED, "long"), response)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testMessageReplyIgnoresReturnImmediately() = runTest(timeout = TEST_TIMEOUT) {
        val reply = Message(
            messageId = "agent-reply",
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart("Hello")),
            contextId = CONTEXT_ID,
        )
        val server = createServer(mapOf("hello" to Script { _, processor -> processor.sendMessage(reply) }))

        assertEquals(reply, server.onSendMessage(request("hello", returnImmediately = true), ctx))
        assertEquals(reply, server.onSendMessage(request("hello", returnImmediately = false), ctx))
        assertEquals(reply, server.onSendMessage(request("hello"), ctx))
    }

    @Test
    fun testExecutorExceptionBeforeTaskCreatedSurfacesToRequester() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("crash" to Script { _, _ -> throw IllegalStateException("Agent crashed") }))

        assertFailsWith<IllegalStateException> {
            server.onSendMessage(request("crash"), ctx)
        }
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    // SendStreamingMessage

    @Test
    fun testStreamingClosesAtEndOfTurnAfterInputRequired() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to gated(askScript)))

        val events = async { server.onSendMessageStreaming(request("ask"), ctx).toList() }
        runCurrent()

        // Input-required is not terminal: the stream stays open until the turn ends.
        assertFalse(events.isCompleted, "The stream must stay open while the turn runs")

        gate.complete(Unit)

        assertEquals(
            listOf<Event>(task(TaskState.TASK_STATE_SUBMITTED, "ask"), status(TaskState.TASK_STATE_INPUT_REQUIRED)),
            events.await()
        )
    }

    @Test
    fun testStreamingClosesAtTerminalStateWhileAgentStillRunning() = runTest(timeout = TEST_TIMEOUT) {
        val completeScript = Script { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "complete"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("complete" to gated(completeScript)))

        val events = server.onSendMessageStreaming(request("complete"), ctx).toList()

        assertEquals(
            listOf<Event>(task(TaskState.TASK_STATE_WORKING, "complete"), status(TaskState.TASK_STATE_COMPLETED)),
            events
        )
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testFollowUpStreamStartsWithTaskSnapshot() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("finish", TASK_ID), ctx).toList()

        // The agent's first event is a status update, so the stored task comes first.
        assertEquals(listOf<Event>(inputRequiredTask) + finishEvents, events)
    }

    @Test
    fun testFollowUpStreamStartingWithTaskHasNoSnapshot() = runTest(timeout = TEST_TIMEOUT) {
        val replaceScript = Script { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "replace"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("ask" to askScript, "replace" to replaceScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("replace", TASK_ID), ctx).toList()

        assertEquals(
            listOf<Event>(task(TaskState.TASK_STATE_WORKING, "replace"), status(TaskState.TASK_STATE_COMPLETED)),
            events
        )
    }

    @Test
    fun testFollowUpStreamSnapshotAppliesHistoryLength() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("finish", TASK_ID, historyLength = 0), ctx).toList()

        assertEquals(listOf<Event>(inputRequiredTask.copy(history = null)) + finishEvents, events)
    }

    @Test
    fun testMessageOnlyStreamHasExactlyOneMessage() = runTest(timeout = TEST_TIMEOUT) {
        val reply = Message(
            messageId = "agent-reply",
            role = Role.ROLE_AGENT,
            parts = listOf(TextPart("Hello")),
            contextId = CONTEXT_ID,
        )
        val server = createServer(mapOf("hello" to gated { _, processor -> processor.sendMessage(reply) }))

        // The stream closes after the message, even though the agent hasn't returned yet.
        val events = server.onSendMessageStreaming(request("hello"), ctx).toList()

        assertEquals(listOf<Event>(reply), events)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testNewTaskStatusUpdateBeforeTaskIsAgentError() = runTest(timeout = TEST_TIMEOUT) {
        val agentFailure = CompletableDeferred<Throwable>()
        val statusFirstScript = Script { _, processor ->
            try {
                processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
            } catch (e: Exception) {
                agentFailure.complete(e)
                throw e
            }
        }
        val server = createServer(mapOf("status" to statusFirstScript))

        val (failure, received) = server.onSendMessageStreaming(request("status"), ctx).collectExpectingFailure()

        // The update for a task that doesn't exist fails in the agent; nothing is streamed.
        assertEquals(TaskOperationException::class, agentFailure.await()::class)
        assertEquals(TaskOperationException::class, failure::class)
        assertEquals(emptyList(), received)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    // Messages referencing a task

    @Test
    fun testUnknownTaskIdFailsWithTaskNotFound() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("finish" to finishScript))

        assertFailsWith<A2ATaskNotFoundException> {
            server.onSendMessage(request("finish", taskId = "unknown"), ctx)
        }
        assertEquals(emptyList(), executed)
    }

    @Test
    fun testMessageToTerminalTaskFailsWithUnsupportedOperation() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("finish" to finishScript))
        taskStorage.update(task(TaskState.TASK_STATE_COMPLETED, "ask"))

        assertFailsWith<A2AUnsupportedOperationException> {
            server.onSendMessage(request("finish", TASK_ID), ctx)
        }

        val (failure, received) = server.onSendMessageStreaming(request("finish", TASK_ID), ctx)
            .collectExpectingFailure()
        assertEquals(A2AUnsupportedOperationException::class, failure::class)
        assertEquals(emptyList(), received)

        assertEquals(emptyList(), executed)
    }

    @Test
    fun testContextIdInferredFromTask() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("finish", TASK_ID, contextId = null), ctx)

        // The follow-up runs in the task's context, not in a generated one.
        assertEquals(listOf(CONTEXT_ID, CONTEXT_ID), executed.map { it.contextId })
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = status(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    agentMessage(TaskState.TASK_STATE_WORKING.name),
                ),
                artifacts = listOf(Artifact(artifactId = "artifact-1", parts = listOf(TextPart("result")))),
            ),
            response
        )
    }

    @Test
    fun testMismatchedContextIdRejected() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        assertFailsWith<A2AInvalidParamsException> {
            server.onSendMessage(request("finish", TASK_ID, contextId = "other-context"), ctx)
        }

        assertEquals(1, executed.size)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
    }

    @Test
    fun testHistoryLengthAppliedToBlockingResponse() = runTest(timeout = TEST_TIMEOUT) {
        val stepsScript = Script { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "steps"))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(status(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("steps" to stepsScript))

        val response = server.onSendMessage(request("steps", historyLength = 1), ctx)

        // The stored history is [user message, WORKING status message]; only the most recent one is returned.
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = status(TaskState.TASK_STATE_INPUT_REQUIRED).status,
                history = listOf(agentMessage(TaskState.TASK_STATE_WORKING.name)),
            ),
            response
        )
    }

    @Test
    fun testNegativeHistoryLengthRejected() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript))

        assertFailsWith<A2AInvalidParamsException> {
            server.onSendMessage(request("ask", historyLength = -1), ctx)
        }
        assertEquals(emptyList(), executed)
    }

    @Test
    fun testFollowUpAgentCanOnlySendTaskEvents() = runTest(timeout = TEST_TIMEOUT) {
        val messageFailure = CompletableDeferred<Throwable>()
        val replyScript = Script { context, processor ->
            // The task exists: a message reply is rejected, a status update without a Task first is accepted.
            messageFailure.complete(
                assertFailsWith<InvalidEventException> {
                    processor.sendMessage(agentMessage("reply").copy(taskId = null, contextId = context.contextId))
                }
            )
            processor.sendTaskEvent(status(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("ask" to askScript, "reply" to replyScript))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("reply", TASK_ID), ctx)

        assertTrue(messageFailure.isCompleted)
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = status(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(userMessage("ask"), agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name)),
            ),
            response
        )
    }
}
