package ai.koog.spring.ai.memory

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository
import org.springframework.ai.chat.messages.ToolResponseMessage

class SpringAiChatHistoryProviderTest {

    private lateinit var repository: InMemoryChatMemoryRepository
    private lateinit var adapter: SpringAiChatHistoryProvider

    @BeforeEach
    fun setUp() {
        repository = InMemoryChatMemoryRepository()
        adapter = SpringAiChatHistoryProvider(
            repository = repository,
            dispatcher = Dispatchers.Unconfined
        )
    }

    // region Passing tests: text round-trip

    @Test
    fun testSystemUserAssistantTextRoundTrip() = runTest {
        val conversationId = "conv-1"
        val messages = listOf(
            Message.System("You are a helpful assistant.", RequestMetaInfo.Empty),
            Message.User("Hello!", RequestMetaInfo.Empty),
            Message.Assistant("Hi there! How can I help you?", ResponseMetaInfo.Empty)
        )

        adapter.store(conversationId, messages)
        val loaded = adapter.load(conversationId)

        assertEquals(3, loaded.size)

        val system = loaded[0] as Message.System
        assertEquals("You are a helpful assistant.", system.content)

        val user = loaded[1] as Message.User
        assertEquals("Hello!", user.content)

        val assistant = loaded[2] as Message.Assistant
        assertEquals("Hi there! How can I help you?", assistant.content)
    }

    @Test
    fun testEmptyConversation() = runTest {
        val result = adapter.load("unknown-conversation")
        assertTrue(result.isEmpty())
    }

    @Test
    fun testStoreEmptyListClearsConversation() = runTest {
        adapter.store("conv-clear", listOf(Message.User("Hello", RequestMetaInfo.Empty)))
        adapter.store("conv-clear", emptyList())

        val loaded = adapter.load("conv-clear")
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun testMultipleConversationsAreIsolated() = runTest {
        adapter.store("conv-a", listOf(Message.User("Hello from A", RequestMetaInfo.Empty)))
        adapter.store("conv-b", listOf(Message.User("Hello from B", RequestMetaInfo.Empty)))

        val loadedA = adapter.load("conv-a")
        val loadedB = adapter.load("conv-b")

        assertEquals(1, loadedA.size)
        assertEquals("Hello from A", loadedA[0].content)
        assertEquals(1, loadedB.size)
        assertEquals("Hello from B", loadedB[0].content)
    }

    @Test
    fun testOverwriteSemantics() = runTest {
        val conversationId = "conv-overwrite"

        adapter.store(conversationId, listOf(Message.User("First", RequestMetaInfo.Empty)))
        adapter.store(conversationId, listOf(Message.User("Second", RequestMetaInfo.Empty)))

        val loaded = adapter.load(conversationId)
        assertEquals(1, loaded.size)
        assertEquals("Second", loaded[0].content)
    }

    // endregion
    // region Filtering tests: non-persistable messages are silently dropped on store

    @Test
    fun testToolCallMessageIsFilteredOnStore() = runTest {
        val messages = listOf(
            Message.User("Hello", RequestMetaInfo.Empty),
            Message.Tool.Call(
                id = "call-1",
                tool = "getWeather",
                content = """{"city":"London"}""",
                metaInfo = ResponseMetaInfo.Empty
            ),
            Message.Tool.Result(
                id = "call-1",
                tool = "getWeather",
                content = """{"temp":15}""",
                metaInfo = RequestMetaInfo.Empty
            ),
            Message.Assistant("The weather is 15°C.", ResponseMetaInfo.Empty)
        )

        adapter.store("conv-tools", messages)
        val loaded = adapter.load("conv-tools")

        assertEquals(2, loaded.size)
        assertEquals("Hello", loaded[0].content)
        assertEquals("The weather is 15°C.", loaded[1].content)
    }

    @Test
    fun testReasoningMessageIsFilteredOnStore() = runTest {
        val messages = listOf(
            Message.User("Explain quantum computing", RequestMetaInfo.Empty),
            Message.Reasoning(
                content = "Let me think step by step...",
                metaInfo = ResponseMetaInfo.Empty
            ),
            Message.Assistant("Quantum computing uses qubits.", ResponseMetaInfo.Empty)
        )

        adapter.store("conv-reasoning", messages)
        val loaded = adapter.load("conv-reasoning")

        assertEquals(2, loaded.size)
        assertEquals("Explain quantum computing", loaded[0].content)
        assertEquals("Quantum computing uses qubits.", loaded[1].content)
    }

    @Test
    fun testMessageWithAttachmentsIsFilteredOnStore() = runTest {
        val imageContent = AttachmentContent.URL("https://example.com/image.png")
        val messages = listOf(
            Message.User(
                parts = listOf(
                    ContentPart.Text("Look at this"),
                    ContentPart.Image(content = imageContent, format = "png")
                ),
                metaInfo = RequestMetaInfo.Empty
            ),
            Message.Assistant("I see an image.", ResponseMetaInfo.Empty)
        )

        adapter.store("conv-attachments", messages)
        val loaded = adapter.load("conv-attachments")

        assertEquals(1, loaded.size)
        assertEquals("I see an image.", loaded[0].content)
    }

    @Test
    fun testAllNonPersistableMessagesFilteredLeavesOnlyTextMessages() = runTest {
        val messages = listOf(
            Message.Tool.Call(id = "c1", tool = "t", content = "{}", metaInfo = ResponseMetaInfo.Empty),
            Message.Tool.Result(id = "c1", tool = "t", content = "{}", metaInfo = RequestMetaInfo.Empty),
            Message.Reasoning(content = "thinking", metaInfo = ResponseMetaInfo.Empty),
        )

        adapter.store("conv-all-filtered", messages)
        val loaded = adapter.load("conv-all-filtered")

        assertTrue(loaded.isEmpty())
    }

    // region Load tests: Spring TOOL messages are skipped

    @Test
    fun testSpringToolMessageIsSkippedOnLoad() = runTest {
        val conversationId = "conv-tool-skip"
        val toolResponse = ToolResponseMessage.ToolResponse("id-1", "search", "result")
        val toolMessage = ToolResponseMessage.builder()
            .responses(listOf(toolResponse))
            .build()

        // Inject a TOOL message directly into the repository, bypassing the store filter
        repository.saveAll(conversationId, listOf(toolMessage))

        val loaded = adapter.load(conversationId)
        assertTrue(loaded.isEmpty(), "TOOL messages should be skipped on load")
    }

    // endregion
}
