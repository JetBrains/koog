package ai.koog.a2a.server.notifications

import ai.koog.a2a.model.PushNotificationConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryPushNotificationConfigStorageTest {
    private lateinit var storage: InMemoryPushNotificationConfigStorage

    @BeforeTest
    fun setUp() {
        storage = InMemoryPushNotificationConfigStorage()
    }

    @Test
    fun testSaveMultipleConfigsWithMixedIds() = runTest {
        val configWithId = PushNotificationConfig(
            id = "config-1",
            url = "https://webhook1.example.com"
        )
        val configWithoutId = PushNotificationConfig(
            id = null,
            url = "https://webhook2.example.com"
        )

        storage.save("task-1", configWithId)
        storage.save("task-1", configWithoutId)

        val retrieved = storage.getAll("task-1")
        assertEquals(setOf(configWithId, configWithoutId), retrieved.toSet())
    }

    @Test
    fun testOverwriteExistingConfig() = runTest {
        val originalConfig = PushNotificationConfig(
            id = "config-1",
            url = "https://webhook1.example.com"
        )
        val updatedConfig = PushNotificationConfig(
            id = "config-1",
            url = "https://webhook-updated.example.com"
        )

        storage.save("task-1", originalConfig)
        storage.save("task-1", updatedConfig)

        val retrieved = storage.getAll("task-1")
        assertEquals(setOf(updatedConfig), retrieved.toSet())
    }

    @Test
    fun testDeleteAllConfigsForTask() = runTest {
        val config = PushNotificationConfig(
            id = "config-1",
            url = "https://webhook.example.com"
        )

        storage.save("task-1", config)
        storage.delete("task-1", null)

        val remaining = storage.getAll("task-1")
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun testDeleteSpecificConfig() = runTest {
        val config1 = PushNotificationConfig(
            id = "config-1",
            url = "https://webhook1.example.com"
        )
        val config2 = PushNotificationConfig(
            id = "config-2",
            url = "https://webhook2.example.com"
        )

        storage.save("task-1", config1)
        storage.save("task-1", config2)
        storage.delete("task-1", "config-1")

        val remaining = storage.getAll("task-1")
        assertEquals(setOf(config2), remaining.toSet())
    }

    @Test
    fun testGetAllForNonExistentTask() = runTest {
        val configs = storage.getAll("non-existent-task")
        assertTrue(configs.isEmpty())
    }
}
