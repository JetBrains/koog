package ai.koog.agents.features.persistence.jdbc

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.serialization.JSONPrimitive
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
abstract class AbstractJdbcPersistenceStorageProviderTest {

    protected abstract fun provider(
        tableName: String = "checkpoints_test",
        ttlSeconds: Long? = null
    ): JdbcPersistenceStorageProvider

    protected fun createTestCheckpoint(
        checkpointId: String = Uuid.random().toString(),
        nodePath: String = "graph/subgraph/node1",
        version: Long = 1L,
        messages: List<Message> = listOf(
            Message.System("You are a helpful assistant", RequestMetaInfo.create(Clock.System)),
            Message.User("Hello", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Hi there!", ResponseMetaInfo.create(Clock.System))
        )
    ): AgentCheckpointData = AgentCheckpointData(
        checkpointId = checkpointId,
        createdAt = Clock.System.now(),
        nodePath = nodePath,
        lastOutput = JSONPrimitive("test-output"),
        messageHistory = messages,
        version = version
    )

    @Test
    fun testSaveAndGetCheckpoints() = runBlocking {
        val p = provider()
        p.migrate()

        val sessionId = "session-1"
        val checkpoint = createTestCheckpoint(version = 1L)
        p.saveCheckpoint(sessionId, checkpoint)

        val loaded = p.getCheckpoints(sessionId)
        assertEquals(1, loaded.size)
        assertEquals(checkpoint.checkpointId, loaded[0].checkpointId)
        assertEquals(checkpoint.nodePath, loaded[0].nodePath)
        assertEquals(checkpoint.version, loaded[0].version)
    }

    @Test
    fun testGetCheckpointsReturnsEmptyForUnknownSession() = runBlocking {
        val p = provider()
        p.migrate()

        val loaded = p.getCheckpoints("nonexistent")
        assertEquals(emptyList(), loaded)
    }

    @Test
    fun testGetLatestCheckpoint() = runBlocking {
        val p = provider()
        p.migrate()

        val sessionId = "session-latest"
        val checkpoint1 = createTestCheckpoint(version = 1L, nodePath = "graph/node1")
        val checkpoint2 = createTestCheckpoint(version = 2L, nodePath = "graph/node2")
        val checkpoint3 = createTestCheckpoint(version = 3L, nodePath = "graph/node3")

        p.saveCheckpoint(sessionId, checkpoint1)
        p.saveCheckpoint(sessionId, checkpoint2)
        p.saveCheckpoint(sessionId, checkpoint3)

        val latest = p.getLatestCheckpoint(sessionId)
        assertNotNull(latest)
        assertEquals(checkpoint3.checkpointId, latest.checkpointId)
        assertEquals(3L, latest.version)
    }

    @Test
    fun testGetLatestCheckpointReturnsNullForUnknownSession() = runBlocking {
        val p = provider()
        p.migrate()

        val latest = p.getLatestCheckpoint("nonexistent")
        assertNull(latest)
    }

    @Test
    fun testSessionIsolation() = runBlocking {
        val p = provider(tableName = "checkpoints_isolation_test")
        p.migrate()

        val checkpoint1 = createTestCheckpoint(version = 1L, nodePath = "graph/nodeA")
        val checkpoint2 = createTestCheckpoint(version = 1L, nodePath = "graph/nodeB")

        p.saveCheckpoint("session-a", checkpoint1)
        p.saveCheckpoint("session-b", checkpoint2)

        val loadedA = p.getCheckpoints("session-a")
        val loadedB = p.getCheckpoints("session-b")

        assertEquals(1, loadedA.size)
        assertEquals("graph/nodeA", loadedA[0].nodePath)

        assertEquals(1, loadedB.size)
        assertEquals("graph/nodeB", loadedB[0].nodePath)
    }

    @Test
    fun testMultipleCheckpointsPerSession() = runBlocking {
        val p = provider(tableName = "checkpoints_multi_test")
        p.migrate()

        val sessionId = "session-multi"
        val checkpoint1 = createTestCheckpoint(version = 1L, nodePath = "graph/step1")
        val checkpoint2 = createTestCheckpoint(version = 2L, nodePath = "graph/step2")
        val checkpoint3 = createTestCheckpoint(version = 3L, nodePath = "graph/step3")

        p.saveCheckpoint(sessionId, checkpoint1)
        p.saveCheckpoint(sessionId, checkpoint2)
        p.saveCheckpoint(sessionId, checkpoint3)

        val loaded = p.getCheckpoints(sessionId)
        assertEquals(3, loaded.size)
    }

    @Test
    fun testMessageSerializationFidelity() = runBlocking {
        val p = provider(tableName = "checkpoints_fidelity_test")
        p.migrate()

        val messages = listOf(
            Message.System("System prompt", RequestMetaInfo.create(Clock.System)),
            Message.User("User input", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Assistant response", ResponseMetaInfo.create(Clock.System)),
            Message.Tool.Call(
                id = "call-1",
                tool = "searchTool",
                content = """{"query": "test"}""",
                metaInfo = ResponseMetaInfo.create(Clock.System)
            ),
            Message.Tool.Result(
                id = "call-1",
                tool = "searchTool",
                content = """{"result": "found"}""",
                metaInfo = RequestMetaInfo.create(Clock.System)
            )
        )

        val checkpoint = createTestCheckpoint(messages = messages)
        p.saveCheckpoint("session-fidelity", checkpoint)

        val loaded = p.getCheckpoints("session-fidelity")
        assertEquals(1, loaded.size)

        val loadedMessages = loaded[0].messageHistory
        assertEquals(5, loadedMessages.size)
        assertTrue(loadedMessages[0] is Message.System)
        assertTrue(loadedMessages[1] is Message.User)
        assertTrue(loadedMessages[2] is Message.Assistant)
        assertTrue(loadedMessages[3] is Message.Tool.Call)
        assertTrue(loadedMessages[4] is Message.Tool.Result)

        val toolCall = loadedMessages[3] as Message.Tool.Call
        assertEquals("call-1", toolCall.id)
        assertEquals("searchTool", toolCall.tool)

        val toolResult = loadedMessages[4] as Message.Tool.Result
        assertEquals("call-1", toolResult.id)
        assertEquals("searchTool", toolResult.tool)
    }

    @Test
    fun testTtlCleanupRemovesExpiredCheckpoints() = runBlocking {
        val p = provider(tableName = "checkpoints_ttl_test", ttlSeconds = 1)
        p.migrate()

        val sessionId = "session-expire"
        p.saveCheckpoint(sessionId, createTestCheckpoint(version = 1L))
        assertEquals(1, p.getCheckpointCount(sessionId))

        delay(1100)
        p.cleanupExpired()

        assertEquals(0, p.getCheckpointCount(sessionId))
        assertEquals(emptyList(), p.getCheckpoints(sessionId))
    }

    @Test
    fun testTtlDoesNotAffectActiveCheckpoints() = runBlocking {
        val p = provider(tableName = "checkpoints_ttl_active_test", ttlSeconds = 5)
        p.migrate()

        p.saveCheckpoint("session-active", createTestCheckpoint(version = 1L))

        delay(500)

        val loaded = p.getCheckpoints("session-active")
        assertEquals(1, loaded.size)
    }

    @Test
    fun testDeleteCheckpoint() = runBlocking {
        val p = provider(tableName = "checkpoints_delete_test")
        p.migrate()

        val sessionId = "session-del"
        val cp1 = createTestCheckpoint(version = 1L)
        val cp2 = createTestCheckpoint(version = 2L)

        p.saveCheckpoint(sessionId, cp1)
        p.saveCheckpoint(sessionId, cp2)
        assertEquals(2, p.getCheckpointCount(sessionId))

        p.deleteCheckpoint(sessionId, cp1.checkpointId)

        assertEquals(1, p.getCheckpointCount(sessionId))
        val remaining = p.getCheckpoints(sessionId)
        assertEquals(cp2.checkpointId, remaining[0].checkpointId)
    }

    @Test
    fun testDeleteAllCheckpoints() = runBlocking {
        val p = provider(tableName = "checkpoints_delete_all_test")
        p.migrate()

        val sessionId = "session-del-all"
        p.saveCheckpoint(sessionId, createTestCheckpoint(version = 1L))
        p.saveCheckpoint(sessionId, createTestCheckpoint(version = 2L))
        p.saveCheckpoint("other-session", createTestCheckpoint(version = 1L))

        assertEquals(2, p.getCheckpointCount(sessionId))

        p.deleteAllCheckpoints(sessionId)

        assertEquals(0, p.getCheckpointCount(sessionId))
        assertEquals(1, p.getCheckpointCount("other-session"))
    }

    @Test
    fun testGetCheckpointCount() = runBlocking {
        val p = provider(tableName = "checkpoints_count_test")
        p.migrate()

        val sessionId = "session-count"
        assertEquals(0, p.getCheckpointCount(sessionId))

        p.saveCheckpoint(sessionId, createTestCheckpoint(version = 1L))
        assertEquals(1, p.getCheckpointCount(sessionId))

        p.saveCheckpoint(sessionId, createTestCheckpoint(version = 2L))
        assertEquals(2, p.getCheckpointCount(sessionId))
    }

    @Test
    fun testUpsertOverwritesExistingCheckpoint() = runBlocking {
        val p = provider(tableName = "checkpoints_upsert_test")
        p.migrate()

        val sessionId = "session-upsert"
        val checkpointId = Uuid.random().toString()

        val original = createTestCheckpoint(
            checkpointId = checkpointId,
            version = 1L,
            nodePath = "graph/original"
        )
        p.saveCheckpoint(sessionId, original)

        val updated = createTestCheckpoint(
            checkpointId = checkpointId,
            version = 2L,
            nodePath = "graph/updated"
        )
        p.saveCheckpoint(sessionId, updated)

        val loaded = p.getCheckpoints(sessionId)
        assertEquals(1, loaded.size)
        assertEquals("graph/updated", loaded[0].nodePath)
        assertEquals(2L, loaded[0].version)
    }

    @Test
    fun testFilterCheckpoints() = runBlocking {
        val p = provider(tableName = "checkpoints_filter_test")
        p.migrate()

        val sessionId = "session-filter"
        val cp1 = createTestCheckpoint(version = 1L, nodePath = "graph/nodeA")
        val cp2 = createTestCheckpoint(version = 2L, nodePath = "graph/nodeB")
        val cp3 = createTestCheckpoint(version = 3L, nodePath = "graph/nodeA")

        p.saveCheckpoint(sessionId, cp1)
        p.saveCheckpoint(sessionId, cp2)
        p.saveCheckpoint(sessionId, cp3)

        val filter = JdbcPersistenceFilter { it.nodePath == "graph/nodeA" }
        val filtered = p.getCheckpoints(sessionId, filter)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.nodePath == "graph/nodeA" })
    }

    @Test
    fun testCheckpointPersistsAcrossProviderInstances() = runBlocking {
        val tableName = "checkpoints_persist_runs_test"
        val sessionId = "persistent-session"

        val run1 = provider(tableName = tableName)
        run1.migrate()

        val cp1 = createTestCheckpoint(version = 1L, nodePath = "graph/step1")
        run1.saveCheckpoint(sessionId, cp1)
        assertEquals(1, run1.getCheckpoints(sessionId).size)

        val run2 = provider(tableName = tableName)
        run2.migrate()

        val loaded = run2.getCheckpoints(sessionId)
        assertEquals(1, loaded.size)
        assertEquals("graph/step1", loaded[0].nodePath)

        val cp2 = createTestCheckpoint(version = 2L, nodePath = "graph/step2")
        run2.saveCheckpoint(sessionId, cp2)

        assertEquals(2, run2.getCheckpoints(sessionId).size)
    }
}
