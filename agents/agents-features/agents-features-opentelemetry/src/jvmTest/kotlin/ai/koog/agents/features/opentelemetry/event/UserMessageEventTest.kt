package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class UserMessageEventTest {

    //region Attributes

    @Test
    fun `test user message attributes`() {
        val expectedContent = "Test message"
        val expectedMessage = createTestUserMessage(expectedContent)
        val llmProvider = MockLLMProvider()

        val userMessageEvent = UserMessageEvent(
            provider = llmProvider,
            message = expectedMessage,
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
    fun `test user message body fields`() {
        val expectedMessage = createTestUserMessage("Test message")

        val userMessageEvent = UserMessageEvent(
            provider = MockLLMProvider(),
            message = expectedMessage,
        )

        val expectedBodyFields = listOf(
            EventBodyFields.Role(role = expectedMessage.role),
            EventBodyFields.Content(content = expectedMessage.content),
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
