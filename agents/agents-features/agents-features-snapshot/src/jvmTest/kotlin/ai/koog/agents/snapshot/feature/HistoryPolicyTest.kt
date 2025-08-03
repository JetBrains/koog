package ai.koog.agents.snapshot.feature

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HistoryPolicyTest {

    @Test
    fun testMessageCountPolicyTrimming() {
        val policy = MessageCountHistoryPolicy(maxMessages = 3)
        
        val messages = listOf(
            Message.System("System message", RequestMetaInfo.create(Clock.System)),
            Message.User("First user message", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("First assistant response", ResponseMetaInfo.create(Clock.System)),
            Message.User("Second user message", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Second assistant response", ResponseMetaInfo.create(Clock.System))
        )
        
        val trimmed = policy.trim(messages)
        
        assertEquals(3, trimmed.size)
        assertEquals("First assistant response", (trimmed[0] as Message.Assistant).content)
        assertEquals("Second user message", (trimmed[1] as Message.User).content)
        assertEquals("Second assistant response", (trimmed[2] as Message.Assistant).content)
    }

    @Test
    fun testMessageCountPolicyNoTrimming() {
        val policy = MessageCountHistoryPolicy(maxMessages = 5)
        
        val messages = listOf(
            Message.System("System message", RequestMetaInfo.create(Clock.System)),
            Message.User("User message", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Assistant response", ResponseMetaInfo.create(Clock.System))
        )
        
        val trimmed = policy.trim(messages)
        
        assertEquals(3, trimmed.size)
        assertEquals(messages, trimmed)
    }

    @Test
    fun testMessageCountPolicyInvalidMaxMessages() {
        assertFailsWith<IllegalArgumentException> {
            MessageCountHistoryPolicy(maxMessages = 0)
        }
        
        assertFailsWith<IllegalArgumentException> {
            MessageCountHistoryPolicy(maxMessages = -1)
        }
    }

    @Test
    fun testNoTrimHistoryPolicy() {
        val policy = NoTrimHistoryPolicy
        
        val messages = listOf(
            Message.System("System message", RequestMetaInfo.create(Clock.System)),
            Message.User("User message", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Assistant response", ResponseMetaInfo.create(Clock.System))
        )
        
        val trimmed = policy.trim(messages)
        
        assertEquals(messages.size, trimmed.size)
        assertEquals(messages, trimmed)
    }

    @Test
    fun testMessageCountPolicyWithEmptyList() {
        val policy = MessageCountHistoryPolicy(maxMessages = 3)
        val messages = emptyList<Message>()
        
        val trimmed = policy.trim(messages)
        
        assertEquals(0, trimmed.size)
        assertEquals(messages, trimmed)
    }

    @Test
    fun testMessageCountPolicyExactLimit() {
        val policy = MessageCountHistoryPolicy(maxMessages = 2)
        
        val messages = listOf(
            Message.User("User message", RequestMetaInfo.create(Clock.System)),
            Message.Assistant("Assistant response", ResponseMetaInfo.create(Clock.System))
        )
        
        val trimmed = policy.trim(messages)
        
        assertEquals(2, trimmed.size)
        assertEquals(messages, trimmed)
    }
}