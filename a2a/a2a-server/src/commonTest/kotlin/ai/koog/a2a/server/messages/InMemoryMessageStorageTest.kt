package ai.koog.a2a.server.messages

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.ctx
import ai.koog.a2a.server.exceptions.MessageOperationException
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InMemoryMessageStorageTest {
    private lateinit var storage: InMemoryMessageStorage

    @BeforeTest
    fun setUp() {
        storage = InMemoryMessageStorage()
    }

    @Test
    fun testSaveMessage() = runTest {
        val message = createMessage("msg-1", "context-1", "Hello, world!")

        storage.save(message, ctx)

        val messages = storage.getByContext("context-1", ctx)

        assertEquals(1, messages.size)
        assertEquals(message, messages[0])
    }

    @Test
    fun testSaveMessageWithoutContextId() = runTest {
        val message = createMessage("msg-1", null, "Hello, world!")

        assertFailsWith<MessageOperationException> {
            storage.save(message, ctx)
        }
    }

    @Test
    fun testSaveMultipleMessages() = runTest {
        val message1 = createMessage("msg-1", "context-1", "First message")
        val message2 = createMessage("msg-2", "context-1", "Second message")
        val message3 = createMessage("msg-3", "context-2", "Different context")

        storage.save(message1, ctx)
        storage.save(message2, ctx)
        storage.save(message3, ctx)

        val context1Messages = storage.getByContext("context-1", ctx)
        assertEquals(2, context1Messages.size)
        assertEquals(message1, context1Messages[0])
        assertEquals(message2, context1Messages[1])

        val context2Messages = storage.getByContext("context-2", ctx)
        assertEquals(1, context2Messages.size)
        assertEquals(message3, context2Messages[0])
    }

    @Test
    fun testGetByNonExistentContext() = runTest {
        val messages = storage.getByContext("non-existent-context", ctx)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun testDeleteByContext() = runTest {
        val message1 = createMessage("msg-1", "context-1", "Message 1")
        val message2 = createMessage("msg-2", "context-1", "Message 2")
        val message3 = createMessage("msg-3", "context-2", "Different context")

        storage.save(message1, ctx)
        storage.save(message2, ctx)
        storage.save(message3, ctx)

        storage.deleteByContext("context-1", ctx)

        val context1Messages = storage.getByContext("context-1", ctx)
        assertTrue(context1Messages.isEmpty())

        val context2Messages = storage.getByContext("context-2", ctx)
        assertEquals(1, context2Messages.size)
        assertEquals(message3, context2Messages[0])
    }

    private fun createMessage(
        messageId: String,
        contextId: String?,
        content: String = "test content"
    ) = Message(
        messageId = messageId,
        role = Role.ROLE_USER,
        parts = listOf(TextPart(content)),
        contextId = contextId
    )
}
