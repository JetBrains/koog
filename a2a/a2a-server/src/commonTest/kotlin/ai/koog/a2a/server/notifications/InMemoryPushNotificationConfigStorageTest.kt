package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.DeleteTaskPushNotificationConfigRequest
import ai.koog.a2a.model.GetTaskPushNotificationConfigRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsRequest
import ai.koog.a2a.model.ListTaskPushNotificationConfigsResponse
import ai.koog.a2a.model.TaskPushNotificationConfig
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryPushNotificationConfigStorageTest {
    private val ctx = ServerCallContext()
    private lateinit var storage: InMemoryPushNotificationConfigStorage

    private val config1 = TaskPushNotificationConfig(
        taskId = "task-1",
        id = "config-1",
        url = "https://webhook1.example.com"
    )
    private val config2 = TaskPushNotificationConfig(
        taskId = "task-1",
        id = "config-2",
        url = "https://webhook2.example.com"
    )

    @BeforeTest
    fun setUp() {
        storage = InMemoryPushNotificationConfigStorage()
    }

    private fun getRequest(taskId: String, id: String) = GetTaskPushNotificationConfigRequest(taskId, id)

    private fun deleteRequest(taskId: String, id: String) = DeleteTaskPushNotificationConfigRequest(taskId, id)

    @Test
    fun testSaveReturnsStoredConfigAndGetReturnsIt() = runTest {
        assertEquals(config1, storage.save(config1, ctx))

        assertEquals(config1, storage.get(getRequest("task-1", "config-1"), ctx))
    }

    @Test
    fun testGetMissingConfigReturnsNull() = runTest {
        storage.save(config1, ctx)

        assertNull(storage.get(getRequest("task-1", "missing"), ctx))
        assertNull(storage.get(getRequest("other-task", "config-1"), ctx))
    }

    @Test
    fun testSameConfigIdInDifferentTasksDoNotCollide() = runTest {
        val otherTaskConfig = config1.copy(taskId = "task-2", url = "https://other.example.com")

        storage.save(config1, ctx)
        storage.save(otherTaskConfig, ctx)

        assertEquals(config1, storage.get(getRequest("task-1", "config-1"), ctx))
        assertEquals(otherTaskConfig, storage.get(getRequest("task-2", "config-1"), ctx))
    }

    @Test
    fun testSaveUpsertsByTaskIdAndConfigId() = runTest {
        val updated = config1.copy(url = "https://webhook-updated.example.com", token = "new-token")

        storage.save(config1, ctx)
        storage.save(updated, ctx)

        assertEquals(listOf(updated), storage.getAll("task-1"))
        assertEquals(updated, storage.get(getRequest("task-1", "config-1"), ctx))
    }

    @Test
    fun testTenantIsPersisted() = runTest {
        val tenantConfig = config1.copy(tenant = "tenant-a")

        storage.save(tenantConfig, ctx)

        assertEquals(tenantConfig, storage.get(getRequest("task-1", "config-1"), ctx))
        assertEquals(listOf(tenantConfig), storage.getAll("task-1"))
    }

    @Test
    fun testListReturnsAllConfigsOfTaskWithEmptyNextPageToken() = runTest {
        val otherTaskConfig = config1.copy(taskId = "task-2")
        storage.save(config1, ctx)
        storage.save(config2, ctx)
        storage.save(otherTaskConfig, ctx)

        val response = storage.list(ListTaskPushNotificationConfigsRequest(taskId = "task-1", pageSize = 1), ctx)

        assertEquals(
            ListTaskPushNotificationConfigsResponse(configs = listOf(config1, config2), nextPageToken = ""),
            response
        )
    }

    @Test
    fun testListForTaskWithoutConfigsIsEmpty() = runTest {
        assertEquals(
            ListTaskPushNotificationConfigsResponse(),
            storage.list(ListTaskPushNotificationConfigsRequest(taskId = "non-existent-task"), ctx)
        )
    }

    @Test
    fun testDeleteSpecificConfig() = runTest {
        storage.save(config1, ctx)
        storage.save(config2, ctx)

        storage.delete(deleteRequest("task-1", "config-1"), ctx)

        assertEquals(listOf(config2), storage.getAll("task-1"))
        assertNull(storage.get(getRequest("task-1", "config-1"), ctx))
    }

    @Test
    fun testDeleteIsIdempotent() = runTest {
        storage.save(config1, ctx)

        storage.delete(deleteRequest("task-1", "config-1"), ctx)
        storage.delete(deleteRequest("task-1", "config-1"), ctx)
        storage.delete(deleteRequest("unknown-task", "unknown-config"), ctx)

        assertEquals(emptyList(), storage.getAll("task-1"))
    }

    @Test
    fun testGetAllForNonExistentTask() = runTest {
        assertEquals(emptyList(), storage.getAll("non-existent-task"))
    }

    @Test
    fun testDeleteAllRemovesOnlyConfigsOfTheTask() = runTest {
        val otherTaskConfig = config1.copy(taskId = "task-2")
        storage.save(config1, ctx)
        storage.save(config2, ctx)
        storage.save(otherTaskConfig, ctx)

        storage.deleteAll("task-1")
        storage.deleteAll("task-1")

        assertEquals(emptyList(), storage.getAll("task-1"))
        assertEquals(listOf(otherTaskConfig), storage.getAll("task-2"))
    }
}
