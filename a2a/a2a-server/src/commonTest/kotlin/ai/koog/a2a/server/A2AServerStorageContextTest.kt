package ai.koog.a2a.server

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.SendMessageConfiguration
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.messages.MessageStorage
import ai.koog.a2a.server.notifications.GatedPushNotificationSender
import ai.koog.a2a.server.notifications.PushNotificationConfigStorage
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.server.turn.TurnEventProcessor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Which call context the server passes to the storages, and the referenced tasks it loads for the agent, checked with
 * the owner-scoped storages.
 */
class A2AServerStorageContextTest {
    private lateinit var taskStorage: OwnerScopedTaskStorage

    /** The referenced tasks of every request context the agent got, SendMessage and CancelTask alike. */
    private val referencedTasks = mutableListOf<List<Task>?>()

    private val replyScript = AgentScript { context, processor ->
        referencedTasks += context.referencedTasks
        processor.sendMessage(agentMessage("reply", taskId = null, contextId = context.contextId))
    }

    private val askScript = AgentScript { _, processor ->
        processor.sendTaskEvent(task(TaskState.TASK_STATE_SUBMITTED))
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_INPUT_REQUIRED, text = null))
    }

    private val failScript = AgentScript { _, processor ->
        processor.sendTaskEvent(statusUpdate(TaskState.TASK_STATE_WORKING, text = null))
        error("Agent failure")
    }

    private val rememberScript = AgentScript { context, processor ->
        context.messageStorage.save(context.params.message)
        processor.sendMessage(agentMessage("${context.messageStorage.getAll().size}", taskId = null))
    }

    /** Runs the scripts, and records the referenced tasks of a cancellation, which it leaves to the server. */
    private inner class Executor(scripts: Map<String, AgentScript>) : AgentExecutor {
        private val scripted = ScriptedAgentExecutor(scripts)

        override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: TurnEventProcessor) =
            scripted.execute(context, eventProcessor)

        override suspend fun cancel(
            context: RequestContext<CancelTaskRequest>,
            eventProcessor: TurnEventProcessor,
            agentJob: Deferred<Unit>?,
        ) {
            referencedTasks += context.referencedTasks
        }
    }

    @BeforeTest
    fun setUp() {
        taskStorage = OwnerScopedTaskStorage()
        referencedTasks.clear()
    }

    private fun TestScope.createServer(
        loadReferencedTasks: Boolean = false,
        messageStorage: MessageStorage = OwnerScopedMessageStorage(),
        pushConfigStorage: PushNotificationConfigStorage? = null,
    ) = A2AServer(
        agentExecutor = Executor(
            mapOf("reply" to replyScript, "ask" to askScript, "fail" to failScript, "remember" to rememberScript)
        ),
        agentCard = testAgentCard(AgentCapabilities(pushNotifications = pushConfigStorage != null)),
        taskStorage = taskStorage,
        messageStorage = messageStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushConfigStorage?.let { GatedPushNotificationSender() },
        idGenerator = FixedIdGenerator(),
        coroutineScope = backgroundScope,
        loadReferencedTasks = loadReferencedTasks,
    )

    private fun replyReferencing(vararg taskIds: String) = request("reply").let {
        it.copy(message = it.message.copy(referenceTaskIds = taskIds.toList()), tenant = "tenant-1")
    }

    /** A completed task with history and artifacts, stored by [owner]. */
    private suspend fun storeReferenced(taskId: String, owner: String): Task {
        val stored = task(TaskState.TASK_STATE_COMPLETED, userText = "about $taskId", taskId = taskId)
            .copy(artifacts = listOf(Artifact(artifactId = "artifact-$taskId", parts = listOf(TextPart(taskId)))))
        taskStorage.update(stored, ownerContext(owner))
        return stored
    }

    @Test
    fun testReferencedTasksAreNotLoadedByDefault() = runTest(timeout = TEST_TIMEOUT) {
        storeReferenced("ref-1", owner = "alice")

        createServer().onSendMessage(replyReferencing("ref-1"), ownerContext("alice"))

        assertEquals(listOf<List<Task>?>(null), referencedTasks)
    }

    @Test
    fun testReferencedTasksAreLoadedInReferenceOrderWithoutDuplicatesOrMissingOnes() = runTest(timeout = TEST_TIMEOUT) {
        val first = storeReferenced("ref-1", owner = "alice")
        val second = storeReferenced("ref-2", owner = "alice")
        val alice = ownerContext("alice")

        createServer(loadReferencedTasks = true)
            .onSendMessage(replyReferencing("ref-2", "missing", "ref-1", "ref-2"), alice)

        assertEquals(listOf<List<Task>?>(listOf(second, first)), referencedTasks)
        assertEquals(
            listOf("ref-2", "missing", "ref-1").map { StorageCall("get $it", alice) },
            taskStorage.calls.filter { it.operation.startsWith("get ref") || it.operation == "get missing" },
        )
    }

    @Test
    fun testAgentDoesNotGetReferencedTaskOfAnotherOwner() = runTest(timeout = TEST_TIMEOUT) {
        val own = storeReferenced("ref-1", owner = "alice")
        storeReferenced("ref-2", owner = "bob")

        createServer(loadReferencedTasks = true)
            .onSendMessage(replyReferencing("ref-2", "ref-1"), ownerContext("alice"))

        assertEquals(listOf<List<Task>?>(listOf(own)), referencedTasks)
    }

    @Test
    fun testCancelTaskHasNoReferencedTasks() = runTest(timeout = TEST_TIMEOUT) {
        val alice = ownerContext("alice")
        val server = createServer(loadReferencedTasks = true)
        server.onSendMessage(request("ask"), alice)

        server.onCancelTask(CancelTaskRequest(TASK_ID), alice)

        assertEquals(listOf<List<Task>?>(null), referencedTasks)
    }

    @Test
    fun testTurnPersistsEventsHistoryAndFailureWithItsOwnContext() = runTest(timeout = TEST_TIMEOUT) {
        val firstTurn = ownerContext("alice")
        val secondTurn = ownerContext("alice")
        val server = createServer()
        server.onSendMessage(request("ask"), firstTurn)

        val failed = server.onSendMessage(request("fail", TASK_ID), secondTurn)

        assertEquals(TaskState.TASK_STATE_FAILED, assertIs<Task>(failed).status.state)
        assertEquals(
            listOf(
                StorageCall("update Task TASK_STATE_SUBMITTED", firstTurn),
                StorageCall("update status TASK_STATE_INPUT_REQUIRED", firstTurn),
                StorageCall("appendHistory user-fail", secondTurn),
                StorageCall("update status TASK_STATE_WORKING", secondTurn),
                StorageCall("update status TASK_STATE_FAILED", secondTurn),
            ),
            taskStorage.writes(),
        )
        // The reads too: none is made with another context.
        assertEquals(listOf(firstTurn, secondTurn), taskStorage.calls.map { it.callContext }.distinct())
    }

    @Test
    fun testForcedCancellationIsPersistedWithContextOfCancelCaller() = runTest(timeout = TEST_TIMEOUT) {
        val turn = ownerContext("alice")
        val cancelCaller = ownerContext("alice")
        val server = createServer()
        server.onSendMessage(request("ask"), turn)

        val canceled = server.onCancelTask(CancelTaskRequest(TASK_ID), cancelCaller)

        assertEquals(TaskState.TASK_STATE_CANCELED, canceled.status.state)
        assertEquals(StorageCall("update status TASK_STATE_CANCELED", cancelCaller), taskStorage.writes().last())
        // The cancellation's reads are made with the caller's context too.
        assertEquals(
            listOf("get $TASK_ID", "update status TASK_STATE_CANCELED"),
            taskStorage.calls.filter { it.callContext === cancelCaller }.map { it.operation }.distinct(),
        )
    }

    @Test
    fun testInlinePushConfigsOfTaskNotCreatedAreDeletedWithContextOfTurn() = runTest(timeout = TEST_TIMEOUT) {
        val pushStorage = OwnerScopedPushConfigStorage()
        val turn = ownerContext("alice")
        val webhook = TaskPushNotificationConfig(url = "https://example.com/webhook")
        val request = request("reply").copy(
            configuration = SendMessageConfiguration(taskPushNotificationConfig = webhook),
        )

        createServer(pushConfigStorage = pushStorage).onSendMessage(request, turn)
        runCurrent()

        assertEquals(
            listOf(StorageCall("save $TASK_ID/$TASK_ID", turn), StorageCall("deleteAll $TASK_ID", turn)),
            pushStorage.calls,
        )
        assertEquals(emptyList(), pushStorage.getAll(TASK_ID))
    }

    @Test
    fun testAgentMessageStorageIsScopedToContextOfTurn() = runTest(timeout = TEST_TIMEOUT) {
        val server = createServer()

        val replies = listOf("alice", "bob", "alice").map { owner ->
            server.onSendMessage(request("remember"), ownerContext(owner))
        }

        // Same context id: each owner sees only the messages saved in their own turns.
        assertEquals(
            listOf("1", "1", "2").map { agentMessage(it, taskId = null) },
            replies,
        )
    }
}
