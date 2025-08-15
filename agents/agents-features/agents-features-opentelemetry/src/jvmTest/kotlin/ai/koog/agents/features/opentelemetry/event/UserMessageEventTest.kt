package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserMessageEventTest {

    //region Attributes

    @Test
    fun `test user message attributes verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestUserMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val userMessageEvent = UserMessageEvent(
            provider = llmProvider,
            message = expectedMessage,
            verbose = false,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, userMessageEvent.attributes.size)
        assertContentEquals(expectedAttributes, userMessageEvent.attributes)
    }

    @Test
    fun `test user message attributes verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestUserMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val userMessageEvent = UserMessageEvent(
            provider = llmProvider,
            message = expectedMessage,
            verbose = true,
        )

        val expectedAttributes = listOf(
            CommonAttributes.System(llmProvider)
        )

        assertEquals(expectedAttributes.size, userMessageEvent.attributes.size)
        assertContentEquals(expectedAttributes, userMessageEvent.attributes)
    }

    //endregion Attributes

    //region Body Fields

    @Test
    fun `test user message body fields verbose false`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestUserMessage(expectedContent)

        val userMessageEvent = UserMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = false,
        )

        assertTrue(userMessageEvent.bodyFields.isEmpty())
    }

    @Test
    fun `test user message body fields verbose true`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestUserMessage(expectedContent)

        val userMessageEvent = UserMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
            verbose = true,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Content(content = expectedContent)
        )

        assertEquals(expectedBodyFields.size, userMessageEvent.bodyFields.size)
        assertContentEquals(expectedBodyFields, userMessageEvent.bodyFields)
    }

    //endregion Body Fields

    //region Private Methods

    private fun createTestUserMessage(content: String): Message.User = Message.User(
        content = content,
        metaInfo = RequestMetaInfo(Clock.System.now())
    )

    //endregion Private Methods
}
