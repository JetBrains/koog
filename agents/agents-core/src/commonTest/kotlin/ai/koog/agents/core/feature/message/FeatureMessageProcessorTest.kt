package ai.koog.agents.core.feature.message

import ai.koog.agents.features.common.message.FeatureEvent
import ai.koog.agents.features.common.message.FeatureMessage
import ai.koog.agents.features.common.message.FeatureMessageProcessor
import ai.koog.agents.features.common.message.FeatureStringMessage
import ai.koog.agents.utils.use
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.js.JsName
import kotlin.test.*

class FeatureMessageProcessorTest {

    class TestFeatureMessageProcessor : FeatureMessageProcessor() {

        val processedMessages = mutableListOf<FeatureMessage>()

        var isClose = false

        override suspend fun processMessage(message: FeatureMessage) {
            processedMessages.add(message)
        }

        override suspend fun close() {
            isClose = true
        }
    }

    class TestFeatureEventMessage(id: String) : FeatureEvent {
        override val eventId: String = id
        override val timestamp: Long get() = Clock.System.now().toEpochMilliseconds()
        override val messageType: FeatureMessage.Type = FeatureMessage.Type.Event
    }

    @Test @JsName("testProcessMessageAddsMessagesToTheList")
    fun `test processMessage adds messages to the list`() = runTest {
        val processor = TestFeatureMessageProcessor()

        val stringMessage1 = FeatureStringMessage("Test message 1")
        val eventMessage1  = TestFeatureEventMessage("Test event 1")
        val stringMessage2 = FeatureStringMessage("Test message 2")
        val eventMessage2  = TestFeatureEventMessage("Test event 2")

        val expectedMessages = listOf(stringMessage1, eventMessage1, stringMessage2, eventMessage2)
        expectedMessages.forEach { message -> processor.processMessage(message) }

        assertEquals(expectedMessages.size, processor.processedMessages.size)
        assertContentEquals(expectedMessages, processor.processedMessages)
    }

    @Test @JsName("testCloseSetsIsCloseFlagToTrue")
    fun `test close sets isClose flag to true`() = runTest {
        val processor = TestFeatureMessageProcessor()
        assertFalse(processor.isClose)

        processor.close()

        assertTrue(processor.isClose)
    }

    @Test @JsName("testCloseMethodIsCalledWithUseMethod")
    fun `test close method is called with with use method`() = runTest {
        val processor = TestFeatureMessageProcessor()
        assertFalse(processor.isClose)
        processor.use { processor -> }

        assertTrue(processor.isClose)
    }
}
