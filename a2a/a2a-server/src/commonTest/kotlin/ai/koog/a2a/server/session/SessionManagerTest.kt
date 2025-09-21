package ai.koog.a2a.server.session

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.PushNotificationConfig
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.notifications.InMemoryPushNotificationConfigStorage
import ai.koog.a2a.server.notifications.PushNotificationSender
import ai.koog.a2a.server.tasks.InMemoryTaskStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionManagerTest {
    private lateinit var taskStorage: InMemoryTaskStorage
    private lateinit var pushConfigStorage: InMemoryPushNotificationConfigStorage
    private lateinit var pushSender: MockPushNotificationSender

    private val contextId = "test-context-1"
    private val taskId = "task-1"

    private class MockPushNotificationSender : PushNotificationSender {
        val sentNotifications = mutableListOf<Pair<PushNotificationConfig, Task>>()

        override suspend fun send(config: PushNotificationConfig, task: Task) {
            sentNotifications.add(config to task)
        }
    }

    @BeforeTest
    fun setUp() {
        taskStorage = InMemoryTaskStorage()
        pushConfigStorage = InMemoryPushNotificationConfigStorage()
        pushSender = MockPushNotificationSender()
    }

    private fun createMessage(
        messageId: String,
        contextId: String,
        content: String
    ) = Message(
        messageId = messageId,
        role = Role.User,
        parts = listOf(TextPart(content)),
        contextId = contextId
    )

    private fun createTask(
        id: String,
        contextId: String,
        state: TaskState = TaskState.Submitted
    ) = Task(
        id = id,
        contextId = contextId,
        status = TaskStatus(
            state = state,
            timestamp = Instant.parse("2023-01-01T10:00:00Z")
        )
    )

    private fun createProcessor(
        contextId: String,
        taskId: String,
        task: Task? = null
    ) = SessionEventProcessor(
        contextId = contextId,
        taskId = taskId,
        taskStorage = taskStorage,
        task = task
    )

    private fun createManager(
        coroutineScope: CoroutineScope,
    ) = SessionManager(
        coroutineScope = coroutineScope,
        taskStorage = taskStorage,
        pushConfigStorage = pushConfigStorage,
        pushSender = pushSender,
    )

    @Test
    fun testSessionManagerCreation() = runTest {
        val sessionManager = SessionManager(
            coroutineScope = this,
            taskStorage = taskStorage
        )

        assertEquals(0, sessionManager.activeSessions())
        assertNull(sessionManager.sessionForTask("any-task-id"))
    }

    @Test
    fun testAddMessageSession() = runTest {
        val sessionManager = createManager(this)
        val eventProcessor = createProcessor(contextId, taskId)

        val message = createMessage("msg-1", contextId, "Hello")

        val session = Session(
            coroutineScope = this,
            eventProcessor = eventProcessor
        ) {
            eventProcessor.sendMessage(message)
        }

        // Start session and wait for completion
        sessionManager.addSession(session)
        session.join()

        // Session should be automatically cleaned up after completion
        assertEquals(0, sessionManager.activeSessions())
    }

    @Test
    fun testAddTaskSession() = runTest {
        val sessionManager = createManager(this)
        val eventProcessor = createProcessor(contextId, taskId)

        val session = Session(
            coroutineScope = this,
            eventProcessor = eventProcessor
        ) {
            val task = createTask(taskId, contextId)
            eventProcessor.sendTaskEvent(task)

            // Simulate work
            delay(400)

            val statusUpdate = TaskStatusUpdateEvent(
                taskId = taskId,
                contextId = contextId,
                status = TaskStatus(state = TaskState.Completed),
                final = true
            )
            eventProcessor.sendTaskEvent(statusUpdate)
        }

        sessionManager.addSession(session)
        session.start()

        assertEquals(session, sessionManager.sessionForTask(taskId))

        session.join()

        // Session should be automatically cleaned up after completion
        assertEquals(0, sessionManager.activeSessions())
    }

    @Test
    fun testMultipleSessions() = runTest {
        val sessionManager = createManager(this)

        // Create two task sessions
        val eventProcessor1 = createProcessor("context-1", "task-1")
        val eventProcessor2 = createProcessor("context-2", "task-2")

        val session1 = Session(
            coroutineScope = this,
            eventProcessor = eventProcessor1
        ) {
            val task = createTask("task-1", "context-1")
            eventProcessor1.sendTaskEvent(task)

            // Simulate work
            delay(150)

            val statusUpdate = TaskStatusUpdateEvent(
                taskId = "task-1",
                contextId = "context-1",
                status = TaskStatus(state = TaskState.Completed),
                final = true
            )
            eventProcessor1.sendTaskEvent(statusUpdate)
        }

        val session2 = Session(
            coroutineScope = this,
            eventProcessor = eventProcessor2
        ) {
            val task = createTask("task-2", "context-2")
            eventProcessor2.sendTaskEvent(task)

            // Simulate work
            delay(150)

            val statusUpdate = TaskStatusUpdateEvent(
                taskId = "task-2",
                contextId = "context-2",
                status = TaskStatus(state = TaskState.Completed),
                final = true
            )
            eventProcessor2.sendTaskEvent(statusUpdate)
        }

        sessionManager.addSession(session1)
        sessionManager.addSession(session2)
        session1.start()
        session2.start()

        assertEquals(session1, sessionManager.sessionForTask("task-1"))
        assertEquals(session2, sessionManager.sessionForTask("task-2"))

        session1.join()
        session2.join()

        // All sessions should be automatically cleaned up
        assertEquals(0, sessionManager.activeSessions())
    }

    @Test
    fun testSessionWithPushNotifications() = runTest {
        val sessionManager = createManager(this)
        val eventProcessor = createProcessor(contextId, taskId)

        // Configure push notification
        val config = PushNotificationConfig(
            id = "config-1",
            url = "https://example.com/webhook"
        )
        pushConfigStorage.save("task-1", config)

        val task = createTask("task-1", contextId)

        val session = Session(
            coroutineScope = this,
            eventProcessor = eventProcessor
        ) {
            eventProcessor.sendTaskEvent(task)

            val statusUpdate = TaskStatusUpdateEvent(
                taskId = "task-1",
                contextId = contextId,
                status = TaskStatus(state = TaskState.Completed),
                final = true
            )
            eventProcessor.sendTaskEvent(statusUpdate)
        }

        sessionManager.addSession(session)
        session.join()

        // Verify push notification was sent
        assertEquals(1, pushSender.sentNotifications.size)
        val (sentConfig, sentTask) = pushSender.sentNotifications[0]
        assertEquals(config, sentConfig)
        assertEquals(TaskState.Completed, sentTask.status.state)
    }
}
