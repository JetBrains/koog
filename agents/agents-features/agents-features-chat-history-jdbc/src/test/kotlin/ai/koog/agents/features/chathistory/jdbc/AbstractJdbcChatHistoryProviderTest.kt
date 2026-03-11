package ai.koog.agents.features.chathistory.jdbc

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

abstract class AbstractJdbcChatHistoryProviderTest {

    protected abstract fun provider(
        tableName: String = "chat_history_test",
        ttlSeconds: Long? = null
    ): JdbcChatHistoryProvider

    protected fun createTestMessages(): List<Message> = listOf(
        Message.System("You are a helpful assistant", RequestMetaInfo.create(Clock.System)),
        Message.User("Hello", RequestMetaInfo.create(Clock.System)),
        Message.Assistant("Hi there! How can I help?", ResponseMetaInfo.create(Clock.System))
    )

    @Test
    fun testStoreAndLoadRoundTrip() = runBlocking {
        val p = provider()
        p.migrate()

        val messages = createTestMessages()
        p.store("conv-1", messages)

        val loaded = p.load("conv-1")
        assertEquals(3, loaded.size)
        assertEquals("You are a helpful assistant", loaded[0].content)
        assertEquals("Hello", loaded[1].content)
        assertEquals("Hi there! How can I help?", loaded[2].content)
    }

    @Test
    fun testLoadReturnsEmptyListForUnknownConversation() = runBlocking {
        val p = provider()
        p.migrate()

        val loaded = p.load("nonexistent")
        assertEquals(emptyList(), loaded)
    }

    @Test
    fun testStoreOverwritesPreviousMessages() = runBlocking {
        val p = provider(tableName = "chat_overwrite_test")
        p.migrate()

        val original = createTestMessages()
        p.store("conv-overwrite", original)

        val updated = listOf(
            Message.User("New message", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("New response", ResponseMetaInfo.create(Clock.System))
        )
        p.store("conv-overwrite", updated)

        val loaded = p.load("conv-overwrite")
        assertEquals(2, loaded.size)
        assertEquals("New message", loaded[0].content)
        assertEquals("New response", loaded[1].content)

        assertEquals(1, p.getConversationCount())
    }

    @Test
    fun testSessionIsolation() = runBlocking {
        val p = provider(tableName = "chat_isolation_test")
        p.migrate()

        val messages1 = listOf(
            Message.User("Hello from conv-1", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Response to conv-1", ResponseMetaInfo.create(Clock.System))
        )
        val messages2 = listOf(
            Message.User("Hello from conv-2", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Response to conv-2", ResponseMetaInfo.create(Clock.System))
        )

        p.store("iso-conv-1", messages1)
        p.store("iso-conv-2", messages2)

        val loaded1 = p.load("iso-conv-1")
        val loaded2 = p.load("iso-conv-2")

        assertEquals(2, loaded1.size)
        assertEquals("Hello from conv-1", loaded1[0].content)

        assertEquals(2, loaded2.size)
        assertEquals("Hello from conv-2", loaded2[0].content)

        assertEquals(2, p.getConversationCount())
    }

    @Test
    fun testMessageSerializationFidelity() = runBlocking {
        val p = provider(tableName = "chat_fidelity_test")
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

        p.store("conv-fidelity", messages)
        val loaded = p.load("conv-fidelity")

        assertEquals(5, loaded.size)

        assertTrue(loaded[0] is Message.System)
        assertTrue(loaded[1] is Message.User)
        assertTrue(loaded[2] is Message.Assistant)
        assertTrue(loaded[3] is Message.Tool.Call)
        assertTrue(loaded[4] is Message.Tool.Result)

        val toolCall = loaded[3] as Message.Tool.Call
        assertEquals("call-1", toolCall.id)
        assertEquals("searchTool", toolCall.tool)

        val toolResult = loaded[4] as Message.Tool.Result
        assertEquals("call-1", toolResult.id)
        assertEquals("searchTool", toolResult.tool)
    }

    @Test
    fun testTtlCleanupRemovesExpiredConversations() = runBlocking {
        val p = provider(tableName = "chat_ttl_test", ttlSeconds = 1)
        p.migrate()

        p.store("will-expire", createTestMessages())
        assertEquals(1, p.getConversationCount())

        delay(1100)
        p.cleanupExpired()

        assertEquals(0, p.getConversationCount())
        assertEquals(emptyList(), p.load("will-expire"))
    }

    @Test
    fun testTtlDoesNotAffectActiveConversations() = runBlocking {
        val p = provider(tableName = "chat_ttl_active_test", ttlSeconds = 5)
        p.migrate()

        p.store("conv-active", createTestMessages())

        delay(500)

        val loaded = p.load("conv-active")
        assertEquals(3, loaded.size)
    }

    @Test
    fun testDeleteHistory() = runBlocking {
        val p = provider(tableName = "chat_delete_test")
        p.migrate()

        p.store("del-conv-1", createTestMessages())
        p.store("del-conv-2", createTestMessages())
        assertEquals(2, p.getConversationCount())

        p.deleteHistory("del-conv-1")

        assertEquals(1, p.getConversationCount())
        assertEquals(emptyList(), p.load("del-conv-1"))
        assertEquals(3, p.load("del-conv-2").size)
    }

    @Test
    fun testGetConversationCount() = runBlocking {
        val p = provider(tableName = "chat_count_test")
        p.migrate()

        assertEquals(0, p.getConversationCount())

        p.store("cnt-conv-1", createTestMessages())
        assertEquals(1, p.getConversationCount())

        p.store("cnt-conv-2", createTestMessages())
        assertEquals(2, p.getConversationCount())

        p.store("cnt-conv-1", createTestMessages())
        assertEquals(2, p.getConversationCount())
    }

    @Test
    fun testStoreEmptyMessageList() = runBlocking {
        val p = provider(tableName = "chat_empty_test")
        p.migrate()

        p.store("conv-empty", emptyList())

        val loaded = p.load("conv-empty")
        assertEquals(emptyList(), loaded)
        assertEquals(1, p.getConversationCount())
    }

    @Test
    fun testConversationPersistsAcrossProviderInstances() = runBlocking {
        val tableName = "chat_persist_runs_test"
        val conversationId = "persistent-session"

        val run1Provider = provider(tableName = tableName)
        run1Provider.migrate()

        val run1Messages = listOf(
            Message.System("You are a helpful assistant.", RequestMetaInfo.create(Clock.System)),
            Message.User("What is the capital of France?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("The capital of France is Paris.", ResponseMetaInfo.create(Clock.System))
        )
        run1Provider.store(conversationId, run1Messages)
        assertEquals(3, run1Provider.load(conversationId).size)

        val run2Provider = provider(tableName = tableName)
        run2Provider.migrate()

        val run2Loaded = run2Provider.load(conversationId)
        assertEquals(3, run2Loaded.size)
        assertEquals("What is the capital of France?", run2Loaded[1].content)

        val run2Messages = run2Loaded + listOf(
            Message.User("And what about Germany?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("The capital of Germany is Berlin.", ResponseMetaInfo.create(Clock.System))
        )
        run2Provider.store(conversationId, run2Messages)

        assertEquals(5, run2Provider.load(conversationId).size)
    }
}
