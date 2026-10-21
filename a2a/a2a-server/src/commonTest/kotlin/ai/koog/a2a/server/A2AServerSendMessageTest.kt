package ai.koog.a2a.server

import ai.koog.a2a.exceptions.A2AContentTypeNotSupportedException
import ai.koog.a2a.exceptions.A2AException
import ai.koog.a2a.exceptions.A2AInternalErrorException
import ai.koog.a2a.exceptions.A2AInvalidAgentResponseException
import ai.koog.a2a.exceptions.A2AInvalidParamsException
import ai.koog.a2a.exceptions.A2ATaskNotFoundException
import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.GetTaskRequest
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.exceptions.InvalidEventException
import ai.koog.a2a.server.exceptions.TaskOperationException
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import ai.koog.a2a.server.tasks.TaskStorage
import ai.koog.a2a.server.turn.IdGenerator
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SendMessage and SendStreamingMessage semantics at the [A2AServer] level: blocking and `returnImmediately`, when
 * streams end and what they begin with, and the rules for messages referencing a task. Uses a scripted agent gated by
 * [CompletableDeferred]s.
 */
class A2AServerSendMessageTest {
    private companion object {
        /** Marks the coroutine of a request whose task reads are recorded, see [RequesterReadRecorder]. */
        private val REQUEST_CALL = CoroutineName("request-call")
    }

    private lateinit var taskStorage: InMemoryTaskStorage

    /** Released by the test to let a gated agent go on. */
    private lateinit var gate: CompletableDeferred<Unit>

    /** Completed when a gated agent has returned from `execute`. */
    private lateinit var agentFinished: CompletableDeferred<Unit>

    private lateinit var executor: ScriptedAgentExecutor

    /** Request contexts of the turns the agent has executed, in order. */
    private val executed get() = executor.executed

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        gate = CompletableDeferred()
        agentFinished = CompletableDeferred()
    }

    private fun TestScope.createServer(
        scripts: Map<String, AgentScript>,
        storage: TaskStorage = taskStorage,
        pushConfigStorage: PushNotificationConfigStorage? = null,
    ): A2AServer {
        executor = ScriptedAgentExecutor(scripts)
        return A2AServer(
            agentExecutor = executor,
            agentCard = testAgentCard(AgentCapabilities(streaming = true, pushNotifications = pushConfigStorage != null)),
            taskStorage = storage,
            pushConfigStorage = pushConfigStorage,
            pushSender = pushConfigStorage?.let { GatedPushNotificationSender() },
            idGenerator = FixedIdGenerator(contextId = "generated-context"),
            coroutineScope = backgroundScope,
        )
    }

    /**
     * Records the reads made in [REQUEST_CALL] coroutines, i.e. on the requester's path, not by the agent, the turn's
     * end, or push notifications: owner-scoped reads as the request and the call context, other reads by name.
     */
    private class RequesterReadRecorder(private val delegate: TaskStorage) : TaskStorage by delegate {
        val reads = mutableListOf<Any>()

        private suspend fun record(read: Any) {
            if (currentCoroutineContext()[CoroutineName] == REQUEST_CALL) reads += read
        }

        override suspend fun get(request: GetTaskRequest, context: ServerCallContext): Task? =
            delegate.get(request, context).also { record(request to context) }

        override suspend fun get(taskId: String, historyLength: Int?, includeArtifacts: Boolean): Task? =
            delegate.get(taskId, historyLength, includeArtifacts).also { record("get $taskId") }

        override suspend fun getAll(taskIds: List<String>, historyLength: Int?, includeArtifacts: Boolean): List<Task> =
            delegate.getAll(taskIds, historyLength, includeArtifacts).also { record("getAll $taskIds") }

        override suspend fun getByContext(contextId: String, historyLength: Int?, includeArtifacts: Boolean): List<Task> =
            delegate.getByContext(contextId, historyLength, includeArtifacts).also { record("getByContext $contextId") }
    }

    /** A standalone reply of the agent: a [Message] instead of a task. */
    private val reply = agentMessage("reply", taskId = null)

    /** Creates the task and leaves it waiting for input; the turn ends. */
    private val askScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "ask"))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
    }

    /** The task stored after [askScript]. */
    private val inputRequiredTask = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED).status,
        history = listOf(userMessage("ask")),
    )

    /** Follow-up turn that completes the task. */
    private val finishScript = AgentScript { _, processor ->
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        processor.sendTaskEvent(artifactChunk("result"))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
    }

    private val finishEvents = listOf(
        statusUpdate(TaskState.TASK_STATE_WORKING),
        artifactChunk("result"),
        statusUpdate(TaskState.TASK_STATE_COMPLETED),
    )

    /** Replies with a [Message] that is not part of a task. */
    private val messageReplyScript = AgentScript { context, processor ->
        processor.sendMessage(agentMessage("reply", taskId = null, contextId = context.contextId))
    }

    /** Runs [script], then waits for [gate] before returning. */
    private fun gated(script: AgentScript) = AgentScript { context, processor ->
        script.run(context, processor)
        gate.await()
        agentFinished.complete(Unit)
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
        val completeScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "complete"))
            processor.sendTaskEvent(artifactChunk("part-1"))
            processor.sendTaskEvent(artifactChunk("part-2"))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("complete" to gated(completeScript)))

        val response = server.onSendMessage(request("complete"), ctx)

        val expected = Task(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            status = statusUpdate(TaskState.TASK_STATE_COMPLETED).status,
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
        val workingScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "work"))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
        }
        val server = createServer(mapOf("work" to workingScript))

        val response = server.onSendMessage(request("work"), ctx)

        val expected = Task(
            id = TASK_ID,
            contextId = CONTEXT_ID,
            status = statusUpdate(TaskState.TASK_STATE_WORKING).status,
            history = listOf(userMessage("work")),
        )
        assertEquals(expected, response)
    }

    @Test
    fun testReturnImmediatelyReturnsWhileAgentGated() = runTest(timeout = TEST_TIMEOUT) {
        val longScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "long"))
            gate.await()
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
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
        val server = createServer(mapOf("hello" to AgentScript { _, processor -> processor.sendMessage(reply) }))

        assertEquals(reply, server.onSendMessage(request("hello", returnImmediately = true), ctx))
        assertEquals(reply, server.onSendMessage(request("hello", returnImmediately = false), ctx))
        assertEquals(reply, server.onSendMessage(request("hello"), ctx))
    }

    @Test
    fun testExecutorExceptionBeforeTaskCreatedSurfacesToRequester() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("crash" to AgentScript { _, _ -> throw IllegalStateException("Agent crashed") }))

        assertFailsWith<IllegalStateException> {
            server.onSendMessage(request("crash"), ctx)
        }
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    /** How the requester sees an error: its type, message, and A2A error code. */
    private fun Throwable.asError() = Triple(this::class, message, (this as? A2AException)?.errorCode)

    @Test
    fun testA2AErrorBeforeNewTaskCreatedSurfacesToRequesterWithoutCreatingTask() = runTest(timeout = TEST_TIMEOUT) {
        val error = A2AContentTypeNotSupportedException("Unsupported content type")
        val server = createServer(mapOf("reject" to AgentScript { _, _ -> throw error }))

        val blockingFailure = assertFailsWith<A2AException> { server.onSendMessage(request("reject"), ctx) }
        val (streamingFailure, received) = server.onSendMessageStreaming(request("reject"), ctx).collectExpectingFailure()

        assertEquals(error.asError(), blockingFailure.asError())
        assertEquals(error.asError(), streamingFailure.asError())
        assertEquals(emptyList(), received)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testA2AErrorBeforeFirstEventOfFollowUpFailsRequestAndLeavesTaskUnchanged() = runTest(timeout = TEST_TIMEOUT) {
        listOf(A2AContentTypeNotSupportedException("Unsupported content type"), A2AInternalErrorException("Agent error"))
            .forEach { error ->
                taskStorage = InMemoryTaskStorage()
                val server = createServer(
                    mapOf("ask" to askScript, "reject" to AgentScript { _, _ -> throw error }, "finish" to finishScript)
                )
                server.onSendMessage(request("ask"), ctx)
                val subscription = subscribe(server).also { it.firstEvent.await() }

                val failure = assertFailsWith<A2AException> { server.onSendMessage(request("reject", TASK_ID), ctx) }
                runCurrent()

                assertEquals(error.asError(), failure.asError())
                assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
                // The subscriber gets nothing and keeps waiting.
                assertEquals(listOf<Event>(inputRequiredTask), subscription.events)
                assertTrue(subscription.job.isActive)
                subscription.job.cancel()

                // The conversation goes on; the rejected message isn't in the history.
                assertEquals(
                    inputRequiredTask.copy(
                        status = statusUpdate(TaskState.TASK_STATE_COMPLETED).status,
                        history = listOf(
                            userMessage("ask"),
                            agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                            userMessage("finish", TASK_ID),
                            agentMessage(TaskState.TASK_STATE_WORKING.name),
                        ),
                        artifacts = listOf(artifactChunk("result").artifact),
                    ),
                    server.onSendMessage(request("finish", TASK_ID), ctx),
                )
            }
    }

    @Test
    fun testA2AErrorBeforeFirstEventOfStreamingFollowUpEmitsNoSnapshot() = runTest(timeout = TEST_TIMEOUT) {
        val error = A2AContentTypeNotSupportedException("Unsupported content type")
        val server = createServer(mapOf("ask" to askScript, "reject" to AgentScript { _, _ -> throw error }))
        server.onSendMessage(request("ask"), ctx)

        val (failure, received) = server.onSendMessageStreaming(request("reject", TASK_ID), ctx).collectExpectingFailure()

        assertEquals(error.asError(), failure.asError())
        assertEquals(emptyList(), received)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFollowUpFailureOtherThanA2AErrorBeforeFirstEventFailsTask() = runTest(timeout = TEST_TIMEOUT) {
        /** The FAILED task the server stores, with [history]; the message id and timestamp are taken from [stored]. */
        fun failedTask(stored: Task?, history: List<Message>): Task {
            val storedStatus = assertNotNull(stored).status
            return inputRequiredTask.copy(
                status = TaskStatus(
                    state = TaskState.TASK_STATE_FAILED,
                    message = Message(
                        messageId = assertNotNull(storedStatus.message).messageId,
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Task failed due to an internal error")),
                        contextId = CONTEXT_ID,
                        taskId = TASK_ID,
                    ),
                    timestamp = storedStatus.timestamp,
                ),
                history = history,
            )
        }

        val askHistory = listOf(userMessage("ask"), agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name))
        listOf(
            // An A2A error after the first event.
            AgentScript { _, processor ->
                processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
                throw A2AContentTypeNotSupportedException("Unsupported content type")
            } to askHistory + listOf(userMessage("fail", TASK_ID), agentMessage(TaskState.TASK_STATE_WORKING.name)),
            // Another exception before the first event: the user message isn't persisted.
            AgentScript { _, _ -> throw IllegalStateException("Agent crashed") } to askHistory,
        ).forEachIndexed { index, (script, history) ->
            taskStorage = InMemoryTaskStorage()
            val server = createServer(mapOf("ask" to askScript, "fail" to script))
            server.onSendMessage(request("ask"), ctx)

            val response = server.onSendMessage(request("fail", TASK_ID), ctx)

            val stored = taskStorage.get(TASK_ID)
            assertEquals(failedTask(stored, history), stored, "case $index")
            assertEquals<Any?>(stored, response, "case $index")
        }
    }

    @Test
    fun testAgentOwnCancellationFailsLikeAnyCrash() = runTest(timeout = TEST_TIMEOUT) {
        /** Runs a blocking SendMessage whose agent creates the task, then fails with [failure]. */
        suspend fun sendFailing(failure: suspend () -> Unit): Pair<Any, Task?> {
            taskStorage = InMemoryTaskStorage()
            val server = createServer(
                mapOf(
                    "work" to AgentScript { _, processor ->
                        processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "work"))
                        failure()
                    }
                )
            )
            return server.onSendMessage(request("work"), ctx) to taskStorage.get(TASK_ID)
        }

        /** The FAILED task the server stores; the message id and timestamp are taken from [stored]. */
        fun failedTask(stored: Task?): Task {
            val storedStatus = assertNotNull(stored).status
            return Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = TaskStatus(
                    state = TaskState.TASK_STATE_FAILED,
                    message = Message(
                        messageId = assertNotNull(storedStatus.message).messageId,
                        role = Role.ROLE_AGENT,
                        parts = listOf(TextPart("Task failed due to an internal error")),
                        contextId = CONTEXT_ID,
                        taskId = TASK_ID,
                    ),
                    timestamp = storedStatus.timestamp,
                ),
                history = listOf(userMessage("work")),
            )
        }

        val (crashResponse, crashStored) = sendFailing { throw IllegalStateException("Agent crashed") }
        assertEquals(failedTask(crashStored), crashStored)
        assertEquals<Any?>(crashStored, crashResponse)

        val (timeoutResponse, timeoutStored) = sendFailing { withTimeout(1) { awaitCancellation() } }
        assertEquals(failedTask(timeoutStored), timeoutStored)
        assertEquals<Any?>(timeoutStored, timeoutResponse)
    }

    @Test
    fun testAgentOwnCancellationBeforeTaskCreatedSurfacesToRequester() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(
            mapOf(
                "crash" to AgentScript { _, _ -> throw IllegalStateException("Agent gave up") },
                "cancel" to AgentScript { _, _ -> throw CancellationException("Agent gave up") },
            )
        )

        val crash = assertFailsWith<IllegalStateException> { server.onSendMessage(request("crash"), ctx) }
        val cancel = assertFailsWith<IllegalStateException> { server.onSendMessage(request("cancel"), ctx) }

        assertEquals(crash.message, cancel.message)
        assertIs<CancellationException>(cancel.cause)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFailingAgentDoesNotCancelNonSupervisorScope() = runTest(timeout = TEST_TIMEOUT) {
        // A plain job, like `CoroutineScope(Dispatchers.IO)`.
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        val idGenerator = object : IdGenerator {
            override suspend fun generateContextId(message: Message): String = CONTEXT_ID
            override suspend fun generateTaskId(message: Message): String = "task-${message.messageId}"
            override suspend fun generateMessageId(taskId: String, contextId: String): String = "server-message"
        }
        fun newTask(context: RequestContext<SendMessageRequest>, state: TaskState) = Task(
            id = context.taskId,
            contextId = context.contextId,
            status = TaskStatus(state = state, timestamp = TIMESTAMP),
        )
        executor = ScriptedAgentExecutor(
            mapOf(
                "crash" to AgentScript { context, processor ->
                    processor.sendTaskEvent(newTask(context, TaskState.TASK_STATE_WORKING))
                    throw IllegalStateException("Agent crashed")
                },
                "complete" to AgentScript { context, processor ->
                    processor.sendTaskEvent(newTask(context, TaskState.TASK_STATE_COMPLETED))
                },
            )
        )
        val server = A2AServer(
            agentExecutor = executor,
            agentCard = testAgentCard(),
            taskStorage = taskStorage,
            idGenerator = idGenerator,
            coroutineScope = scope,
        )

        val failedResponse = server.onSendMessage(request("crash"), ctx)
        val failedTask = assertNotNull(taskStorage.get("task-user-crash"))
        val failedStatus = TaskStatus(
            state = TaskState.TASK_STATE_FAILED,
            message = Message(
                messageId = "server-message",
                role = Role.ROLE_AGENT,
                parts = listOf(TextPart("Task failed due to an internal error")),
                contextId = CONTEXT_ID,
                taskId = "task-user-crash",
            ),
            timestamp = failedTask.status.timestamp,
        )
        assertEquals(failedStatus, failedTask.status)
        assertEquals(failedTask, failedResponse)

        val completedTask = Task(
            id = "task-user-complete",
            contextId = CONTEXT_ID,
            status = TaskStatus(state = TaskState.TASK_STATE_COMPLETED, timestamp = TIMESTAMP),
            history = listOf(userMessage("complete").copy(taskId = "task-user-complete")),
        )
        assertEquals(completedTask, server.onSendMessage(request("complete"), ctx))
        assertTrue(scope.isActive)
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
            listOf<Event>(task(TaskState.TASK_STATE_SUBMITTED, "ask"), statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED)),
            events.await()
        )
    }

    @Test
    fun testStreamingClosesAtTerminalStateWhileAgentStillRunning() = runTest(timeout = TEST_TIMEOUT) {
        val completeScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_WORKING, "complete"))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("complete" to gated(completeScript)))

        val events = server.onSendMessageStreaming(request("complete"), ctx).toList()

        assertEquals(
            listOf<Event>(task(TaskState.TASK_STATE_WORKING, "complete"), statusUpdate(TaskState.TASK_STATE_COMPLETED)),
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

        // The snapshot is taken when the turn view is registered: the follow-up message is persisted later, with the
        // agent's first event.
        assertEquals(listOf<Event>(inputRequiredTask) + finishEvents, events)
    }

    @Test
    fun testTaskEventForExistingTaskIsIgnoredAndFollowUpStreamStartsWithSnapshot() = runTest(timeout = TEST_TIMEOUT) {
        val replaceScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_COMPLETED, "replace"))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("ask" to askScript, "replace" to replaceScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("replace", TASK_ID), ctx).toList()

        // The Task replacement is neither streamed nor stored (not even its terminal state), so the stream begins with
        // the snapshot.
        assertEquals(
            listOf<Event>(
                inputRequiredTask,
                statusUpdate(TaskState.TASK_STATE_WORKING),
                statusUpdate(TaskState.TASK_STATE_COMPLETED),
            ),
            events
        )
        assertEquals(
            inputRequiredTask.copy(
                status = statusUpdate(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    userMessage("replace", TASK_ID),
                    agentMessage(TaskState.TASK_STATE_WORKING.name),
                ),
            ),
            taskStorage.get(TASK_ID)
        )
    }

    @Test
    fun testFollowUpWhoseOnlyEventIsIgnoredTaskStreamsSnapshotOnly() = runTest(timeout = TEST_TIMEOUT) {
        val replaceScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_COMPLETED, "replace"))
        }
        val server = createServer(mapOf("ask" to askScript, "replace" to replaceScript))
        server.onSendMessage(request("ask"), ctx)

        val events = server.onSendMessageStreaming(request("replace", TASK_ID), ctx).toList()

        // The ignored Task is not a status or artifact update: the user message is not persisted.
        assertEquals(listOf<Event>(inputRequiredTask), events)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
    }

    @Test
    fun testFollowUpWithoutAgentEventsDoesNotPersistUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "silent" to AgentScript { _, _ -> }))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("silent", TASK_ID), ctx)

        assertEquals(inputRequiredTask, response)
        assertEquals(inputRequiredTask, taskStorage.get(TASK_ID))
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
        val server = createServer(mapOf("hello" to gated { _, processor -> processor.sendMessage(reply) }))

        // The stream closes after the message, even though the agent hasn't returned yet.
        val events = server.onSendMessageStreaming(request("hello"), ctx).toList()

        assertEquals(listOf<Event>(reply), events)
        assertFalse(agentFinished.isCompleted, "The agent must still be running")

        gate.complete(Unit)
        agentFinished.await()
    }

    @Test
    fun testNewTaskStatusUpdateBeforeTaskIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val agentFailure = CompletableDeferred<Throwable>()
        val statusFirstScript = AgentScript { _, processor ->
            try {
                processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
            } catch (e: Exception) {
                agentFailure.complete(e)
                throw e
            }
        }
        val server = createServer(mapOf("status" to statusFirstScript))

        val (failure, received) = server.onSendMessageStreaming(request("status"), ctx).collectExpectingFailure()
        assertFailsWith<A2AInvalidAgentResponseException> { server.onSendMessage(request("status"), ctx) }

        // The agent still gets the storage exception; the requester gets an invalid agent response.
        assertEquals(TaskOperationException::class, agentFailure.await()::class)
        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
        assertEquals(emptyList(), received)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testSwallowedRejectedEventIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val swallowing = AgentScript { _, processor ->
            runCatching { processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING)) }
        }
        val server = createServer(mapOf("status" to swallowing))

        assertFailsWith<A2AInvalidAgentResponseException> {
            server.onSendMessage(request("status"), ctx)
        }
        val (failure, _) = server.onSendMessageStreaming(request("status"), ctx).collectExpectingFailure()
        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
    }

    @Test
    fun testNewTaskTurnWithoutEventsIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("silent" to AgentScript { _, _ -> }))

        assertFailsWith<A2AInvalidAgentResponseException> { server.onSendMessage(request("silent"), ctx) }
        val (failure, received) = server.onSendMessageStreaming(request("silent"), ctx).collectExpectingFailure()

        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
        assertEquals(emptyList(), received)
        assertEquals(null, taskStorage.get(TASK_ID))
    }

    @Test
    fun testMessageReplyForExistingTaskIsInvalidAgentResponse() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "reply" to messageReplyScript))
        server.onSendMessage(request("ask"), ctx)

        assertFailsWith<A2AInvalidAgentResponseException> {
            server.onSendMessage(request("reply", TASK_ID), ctx)
        }
        assertEquals(TaskState.TASK_STATE_FAILED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
    }

    @Test
    fun testMessageReplyForExistingTaskIsInvalidAgentResponseWhenStreaming() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer(mapOf("ask" to askScript, "reply" to messageReplyScript))
        server.onSendMessage(request("ask"), ctx)

        val (failure, received) = server.onSendMessageStreaming(request("reply", TASK_ID), ctx).collectExpectingFailure()

        assertEquals(A2AInvalidAgentResponseException::class, failure::class)
        assertEquals(emptyList(), received)
    }

    // Requester cancellation

    @Test
    fun testRequesterCancelledRightAfterTurnStartDoesNotBlockAgent() = runTest(timeout = TEST_TIMEOUT) {
        lateinit var requester: Job
        // Cancels the requester while the turn saves its inline push config: the turn's view is registered, so the
        // cancellation is first observed after the turn has started, before the requester collects the view.
        val pushStorage = InMemoryPushNotificationConfigStorage()
        val cancellingPushStorage = object : PushNotificationConfigStorage by pushStorage {
            override suspend fun save(config: TaskPushNotificationConfig, context: ServerCallContext) =
                pushStorage.save(config, context).also { requester.cancel() }
        }
        val server = createServer(
            mapOf("ask" to askScript, "finish" to gated(finishScript)),
            pushConfigStorage = cancellingPushStorage,
        )
        server.onSendMessage(request("ask"), ctx)
        gate.complete(Unit)

        val followUp = request("finish", TASK_ID).copy(
            configuration = SendMessageConfiguration(
                taskPushNotificationConfig = TaskPushNotificationConfig(url = "https://example.com/hook"),
            ),
        )
        requester = launch(start = CoroutineStart.LAZY) { server.onSendMessage(followUp, ctx) }
        requester.join()
        runCurrent()

        assertTrue(requester.isCancelled)
        // Nobody collects the requester's view: the agent's events must not wait for it.
        assertTrue(agentFinished.isCompleted, "The agent must not be blocked by the abandoned turn view")
        assertEquals(TaskState.TASK_STATE_COMPLETED, assertNotNull(taskStorage.get(TASK_ID)).status.state)
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
                status = statusUpdate(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    // Stored with the task id and the context id of the task, though the client sent no context id.
                    userMessage("finish", TASK_ID),
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
    fun testStreamingFollowUpReadsTaskOnceForCaller() = runTest(timeout = TEST_TIMEOUT) {
        val storage = RequesterReadRecorder(taskStorage)
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript), storage)
        server.onSendMessage(request("ask"), ctx)
        val callerCtx = ServerCallContext()
        val followUp = request("finish", TASK_ID, historyLength = 0).copy(tenant = "tenant-1")

        val events = withContext(REQUEST_CALL) { server.onSendMessageStreaming(followUp, callerCtx).toList() }

        // The one read validates the task, and is the snapshot and the agent's task.
        assertEquals(listOf<Any>(GetTaskRequest(id = TASK_ID, tenant = "tenant-1") to callerCtx), storage.reads)
        assertEquals(listOf<Event>(inputRequiredTask.copy(history = null)) + finishEvents, events)
        assertEquals(inputRequiredTask, executed[1].task)
    }

    @Test
    fun testBlockingResultIsReadForCaller() = runTest(timeout = TEST_TIMEOUT) {
        val storage = RequesterReadRecorder(taskStorage)
        val server = createServer(mapOf("ask" to askScript, "finish" to finishScript), storage)
        val callerCtx = ServerCallContext()

        withContext(REQUEST_CALL) {
            server.onSendMessage(request("ask", historyLength = 1).copy(tenant = "tenant-1"), callerCtx)
            server.onSendMessage(request("finish", TASK_ID, historyLength = 1).copy(tenant = "tenant-1"), callerCtx)
        }

        // Like GetTask: with the caller's context and tenant, and the requested history length.
        val result = GetTaskRequest(id = TASK_ID, historyLength = 1, tenant = "tenant-1") to callerCtx
        val followUpValidation = GetTaskRequest(id = TASK_ID, tenant = "tenant-1") to callerCtx
        assertEquals(listOf<Any>(result, followUpValidation, result), storage.reads)
    }

    @Test
    fun testHistoryLengthAppliedToBlockingResponse() = runTest(timeout = TEST_TIMEOUT) {
        val stepsScript = AgentScript { _, processor ->
            processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED, "steps"))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("steps" to stepsScript))

        val response = server.onSendMessage(request("steps", historyLength = 1), ctx)

        // The stored history is [user message, WORKING status message]; only the most recent one is returned.
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED).status,
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
        val replyScript = AgentScript { context, processor ->
            // The task exists: a message reply is rejected, a status update without a Task first is accepted.
            messageFailure.complete(assertFailsWith<InvalidEventException> { messageReplyScript.run(context, processor) })
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("ask" to askScript, "reply" to replyScript))
        server.onSendMessage(request("ask"), ctx)

        val response = server.onSendMessage(request("reply", TASK_ID), ctx)

        assertTrue(messageFailure.isCompleted)
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                status = statusUpdate(TaskState.TASK_STATE_COMPLETED).status,
                history = listOf(
                    userMessage("ask"),
                    agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
                    userMessage("reply", TASK_ID),
                ),
            ),
            response
        )
    }

    // History persistence and RequestContext.task

    private fun bareTask(state: TaskState, message: Message? = null) = Task(
        id = TASK_ID,
        contextId = CONTEXT_ID,
        status = TaskStatus(state = state, message = message, timestamp = TIMESTAMP),
    )

    private suspend fun A2AServer.history(): List<Message>? =
        onGetTask(GetTaskRequest(id = TASK_ID), ctx).history

    @Test
    fun testServerPersistsUserMessageOfNewTaskWhenAgentOmitsIt() = runTest(timeout = TEST_TIMEOUT) {
        val bareScript = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_SUBMITTED))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("bare" to bareScript))

        val response = server.onSendMessage(request("bare"), ctx)

        assertEquals(
            bareTask(TaskState.TASK_STATE_INPUT_REQUIRED, agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name))
                .copy(history = listOf(userMessage("bare", TASK_ID))),
            response
        )
        assertEquals(listOf(userMessage("bare", TASK_ID)), server.history())
    }

    @Test
    fun testInitialTaskStreamedToRequesterCarriesUserMessage() = runTest(timeout = TEST_TIMEOUT) {
        val bareScript = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_COMPLETED))
        }
        val server = createServer(mapOf("bare" to bareScript))

        val events = server.onSendMessageStreaming(request("bare"), ctx).toList()

        assertEquals(
            listOf<Event>(
                bareTask(TaskState.TASK_STATE_WORKING).copy(history = listOf(userMessage("bare", TASK_ID))),
                statusUpdate(TaskState.TASK_STATE_COMPLETED),
            ),
            events
        )
    }

    @Test
    fun testHistoryAfterInputRequiredTurnAndFollowUpIsInOrderWithoutDuplicates() = runTest(timeout = TEST_TIMEOUT) {
        val askBare = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_SUBMITTED))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("ask" to askBare, "finish" to finishScript))

        server.onSendMessage(request("ask"), ctx)
        val response = server.onSendMessage(request("finish", TASK_ID), ctx)

        val expectedHistory = listOf(
            userMessage("ask", TASK_ID),
            agentMessage(TaskState.TASK_STATE_INPUT_REQUIRED.name),
            userMessage("finish", TASK_ID),
            agentMessage(TaskState.TASK_STATE_WORKING.name),
        )
        assertEquals(expectedHistory, response.let { (it as Task).history })
        assertEquals(expectedHistory, server.history())
        assertEquals(expectedHistory.size, expectedHistory.map { it.messageId }.toSet().size)
    }

    @Test
    fun testAgentThatAlreadyIncludesUserMessageDoesNotDuplicateIt() = runTest(timeout = TEST_TIMEOUT) {
        // askScript creates the task with the user message in the history, and the same user message as the status
        // message of a Task, which the storage moves into the history on the next status update.
        val selfPersisting = AgentScript { _, processor ->
            val user = userMessage("self")
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_WORKING, user).copy(history = listOf(user)))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("self" to selfPersisting))

        server.onSendMessage(request("self"), ctx)

        assertEquals(listOf(userMessage("self")), server.history())
    }

    @Test
    fun testFollowUpRequestContextTaskIsFullStoredTask() = runTest(timeout = TEST_TIMEOUT) {
        val artifact = Artifact(artifactId = "artifact-1", parts = listOf(TextPart("draft")))
        val askWithArtifact = AgentScript { _, processor ->
            processor.sendTaskEvent(bareTask(TaskState.TASK_STATE_WORKING))
            processor.sendTaskEvent(artifactChunk("draft"))
            processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED))
        }
        val server = createServer(mapOf("ask" to askWithArtifact, "finish" to finishScript))
        server.onSendMessage(request("ask"), ctx)

        server.onSendMessage(request("finish", TASK_ID), ctx)

        assertEquals(null, executed[0].task)
        assertEquals(
            Task(
                id = TASK_ID,
                contextId = CONTEXT_ID,
                // As stored when the turn starts: the follow-up message is not in it, and the status message is still
                // the status message.
                status = statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED).status,
                history = listOf(userMessage("ask", TASK_ID)),
                artifacts = listOf(artifact),
            ),
            executed[1].task
        )
    }
}
