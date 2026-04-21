package ai.koog.agents.features.chatmemory.sql

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.test.utils.DockerAvailableCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(DockerAvailableCondition::class)
@Execution(ExecutionMode.SAME_THREAD)
class PostgresChatHistoryProviderTest {

    private lateinit var postgres: PostgreSQLContainer<*>
    private lateinit var database: Database

    @BeforeAll
    fun setUp() {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
        postgres.start()

        database = Database.connect(
            url = postgres.jdbcUrl,
            driver = "org.postgresql.Driver",
            user = postgres.username,
            password = postgres.password
        )
    }

    @AfterAll
    fun tearDown() {
        postgres.stop()
    }

    private fun provider(
        tableName: String = "chat_history_test",
        ttlSeconds: Long? = null
    ): PostgresChatHistoryProvider {
        return PostgresChatHistoryProvider(
            database = database,
            tableName = tableName,
            ttlSeconds = ttlSeconds
        )
    }

    private fun createTestMessages(): List<Message> = listOf(
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

        // Overwrite should not increase count
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
    fun testChatConversationGrowsAcrossTurns() = runBlocking {
        val p = provider(tableName = "chat_grow_test")
        p.migrate()

        val conversationId = "multi-turn-chat"
        val messages = mutableListOf<Message>()

        // Turn 1: system prompt + first user message + assistant reply
        messages += Message.System("You are a coding assistant.", RequestMetaInfo.create(Clock.System))
        messages += Message.User("Write me a hello world in Kotlin", RequestMetaInfo.create(Clock.System))
        messages += Message.Assistant("Here you go:\n```kotlin\nfun main() = println(\"Hello, World!\")\n```", ResponseMetaInfo.create(Clock.System))
        p.store(conversationId, messages)

        var loaded = p.load(conversationId)
        assertEquals(3, loaded.size)

        // Turn 2: user follow-up with tool usage
        messages += Message.User("Now run it for me", RequestMetaInfo.create(Clock.System))
        messages += Message.Tool.Call(
            id = "exec-1",
            tool = "codeRunner",
            content = """{"language":"kotlin","code":"fun main() = println(\"Hello, World!\")"}""",
            metaInfo = ResponseMetaInfo.create(Clock.System)
        )
        messages += Message.Tool.Result(
            id = "exec-1",
            tool = "codeRunner",
            content = """{"output":"Hello, World!","exitCode":0}""",
            metaInfo = RequestMetaInfo.create(Clock.System)
        )
        messages += Message.Assistant("Done! The output is:\n```\nHello, World!\n```", ResponseMetaInfo.create(Clock.System))
        p.store(conversationId, messages)

        loaded = p.load(conversationId)
        assertEquals(7, loaded.size)

        // Turn 3: another follow-up
        messages += Message.User("Can you add a greeting parameter?", RequestMetaInfo.create(Clock.System))
        messages += Message.Assistant("Sure:\n```kotlin\nfun greet(name: String) = println(\"Hello, \$name!\")\n```", ResponseMetaInfo.create(Clock.System))
        p.store(conversationId, messages)

        loaded = p.load(conversationId)
        assertEquals(9, loaded.size)

        // Verify the full conversation is coherent
        assertTrue(loaded[0] is Message.System)
        assertEquals("You are a coding assistant.", loaded[0].content)
        assertTrue(loaded[4] is Message.Tool.Call)
        assertEquals("codeRunner", (loaded[4] as Message.Tool.Call).tool)
        assertTrue(loaded[5] is Message.Tool.Result)
        assertEquals("Can you add a greeting parameter?", loaded[7].content)

        // Still a single conversation row
        assertEquals(1, p.getConversationCount())
    }

    @Test
    fun testConversationPersistsAcrossProviderInstances() = runBlocking {
        val tableName = "chat_persist_runs_test"
        val conversationId = "persistent-session"

        // --- Run 1: initial conversation ---
        val run1Provider = provider(tableName = tableName)
        run1Provider.migrate()

        val run1Messages = listOf(
            Message.System("You are a helpful assistant.", RequestMetaInfo.create(Clock.System)),
            Message.User("What is the capital of France?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("The capital of France is Paris.", ResponseMetaInfo.create(Clock.System))
        )
        run1Provider.store(conversationId, run1Messages)

        assertEquals(3, run1Provider.load(conversationId).size)

        // --- Run 2: new provider instance, load and continue ---
        val run2Provider = provider(tableName = tableName)
        run2Provider.migrate()

        val run2Loaded = run2Provider.load(conversationId)
        assertEquals(3, run2Loaded.size)
        assertEquals("What is the capital of France?", run2Loaded[1].content)
        assertEquals("The capital of France is Paris.", run2Loaded[2].content)

        val run2Messages = run2Loaded + listOf(
            Message.User("And what about Germany?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("The capital of Germany is Berlin.", ResponseMetaInfo.create(Clock.System))
        )
        run2Provider.store(conversationId, run2Messages)

        assertEquals(5, run2Provider.load(conversationId).size)

        // --- Run 3: yet another fresh provider ---
        val run3Provider = provider(tableName = tableName)
        run3Provider.migrate()

        val run3Loaded = run3Provider.load(conversationId)
        assertEquals(5, run3Loaded.size)

        assertEquals("You are a helpful assistant.", run3Loaded[0].content)
        assertEquals("What is the capital of France?", run3Loaded[1].content)
        assertEquals("The capital of France is Paris.", run3Loaded[2].content)
        assertEquals("And what about Germany?", run3Loaded[3].content)
        assertEquals("The capital of Germany is Berlin.", run3Loaded[4].content)

        assertTrue(run3Loaded[0] is Message.System)
        assertTrue(run3Loaded[1] is Message.User)
        assertTrue(run3Loaded[2] is Message.Assistant)
        assertTrue(run3Loaded[3] is Message.User)
        assertTrue(run3Loaded[4] is Message.Assistant)
    }

    @Test
    fun testMultipleConversationsPersistAcrossRuns() = runBlocking {
        val tableName = "chat_multi_persist_test"

        // --- Run 1: start two independent conversations ---
        val run1 = provider(tableName = tableName)
        run1.migrate()

        run1.store(
            "agent-alice",
            listOf(
                Message.System("You help with math.", RequestMetaInfo.create(Clock.System)),
                Message.User("What is 2+2?", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("4", ResponseMetaInfo.create(Clock.System))
            )
        )

        run1.store(
            "agent-bob",
            listOf(
                Message.System("You help with history.", RequestMetaInfo.create(Clock.System)),
                Message.User("When was the moon landing?", RequestMetaInfo.create(Clock.System)),
                Message.Assistant("July 20, 1969.", ResponseMetaInfo.create(Clock.System))
            )
        )

        assertEquals(2, run1.getConversationCount())

        // --- Run 2: continue both from a fresh provider ---
        val run2 = provider(tableName = tableName)
        run2.migrate()

        val aliceHistory = run2.load("agent-alice")
        assertEquals(3, aliceHistory.size)
        val aliceUpdated = aliceHistory + listOf(
            Message.User("And 3+3?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("6", ResponseMetaInfo.create(Clock.System))
        )
        run2.store("agent-alice", aliceUpdated)

        val bobHistory = run2.load("agent-bob")
        assertEquals(3, bobHistory.size)
        val bobUpdated = bobHistory + listOf(
            Message.User("Who was the first person on the moon?", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Neil Armstrong.", ResponseMetaInfo.create(Clock.System))
        )
        run2.store("agent-bob", bobUpdated)

        // --- Run 3: verify everything is intact ---
        val run3 = provider(tableName = tableName)
        run3.migrate()

        assertEquals(2, run3.getConversationCount())

        val aliceFinal = run3.load("agent-alice")
        assertEquals(5, aliceFinal.size)
        assertEquals("What is 2+2?", aliceFinal[1].content)
        assertEquals("And 3+3?", aliceFinal[3].content)
        assertEquals("6", aliceFinal[4].content)

        val bobFinal = run3.load("agent-bob")
        assertEquals(5, bobFinal.size)
        assertEquals("When was the moon landing?", bobFinal[1].content)
        assertEquals("Who was the first person on the moon?", bobFinal[3].content)
        assertEquals("Neil Armstrong.", bobFinal[4].content)
    }

    @Test
    fun testBuilderApi() = runBlocking {
        val p = PostgresChatHistoryProvider.builder()
            .database(database)
            .tableName("chat_builder_test")
            .build()
        p.migrate()

        p.store("builder-conv", createTestMessages())

        val loaded = p.load("builder-conv")
        assertEquals(3, loaded.size)
        assertEquals("Hello", loaded[1].content)
    }
}
