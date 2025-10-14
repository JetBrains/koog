package ai.koog.agents.core.feature.message

import ai.koog.agents.core.feature.mock.TestFeatureEventMessage
import ai.koog.agents.core.feature.mock.TestFeatureMessageProcessor
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureMessageProcessorTest {

    //region onMessage

    @Test
    @JsName("testOnMessageAddsMessagesToTheList")
    fun `test onMessage adds messages to the list`() = runTest {
        val processor = TestFeatureMessageProcessor()

        val stringMessage1 = FeatureStringMessage("Test message 1")
        val eventMessage1 = TestFeatureEventMessage("Test event 1")
        val stringMessage2 = FeatureStringMessage("Test message 2")
        val eventMessage2 = TestFeatureEventMessage("Test event 2")

        val expectedMessages = listOf(stringMessage1, eventMessage1, stringMessage2, eventMessage2)
        expectedMessages.forEach { message -> processor.onMessage(message) }

        assertEquals(expectedMessages.size, processor.processedMessages.size)
        assertContentEquals(expectedMessages, processor.processedMessages)
    }

    //endregion onMessage

    //region isOpen

    @Test
    @JsName("testDefaultCloseSetsIsOpenFlagToFalse")
    fun `test default close sets isOpen flag to false`() = runTest {
        TestFeatureMessageProcessor().use { processor ->
            processor.initialize()
            assertTrue(processor.isOpen.value)

            processor.close()
            assertFalse(processor.isOpen.value)
        }
    }

    @Test
    @JsName("testIsOpenFlagReturnCurrentStatus")
    fun `test isOpen flag return current status`() = runTest {
        TestFeatureMessageProcessor().use { processor ->
            assertFalse(processor.isOpen.value)

            processor.initialize()
            assertTrue(processor.isOpen.value)
        }
    }

    //endregion isOpen

    //region Close

    @Test
    @JsName("testCloseSetsIsOpenFlagToFalseByDefault")
    fun `test close sets isOpen flag to false by default`() = runTest {
        val processor = TestFeatureMessageProcessor()
        assertFalse(processor.isOpen.value)

        processor.close()
        assertFalse(processor.isOpen.value)
    }

    @Test
    @JsName("testCloseMethodIsCalledWithUseMethod")
    fun `test close method is called with with use method`() = runTest {
        val processor = TestFeatureMessageProcessor()
        assertFalse(processor.isOpen.value)

        processor.initialize()
        assertTrue(processor.isOpen.value)

        processor.use { }
        assertFalse(processor.isOpen.value)
    }

    //endregion Close

    //region Filter

    @Test
    @JsName("testAllMessagesCollectedWithDefaultFilter")
    fun `test all messages collected with default filter`() = runTest {
        val processor = TestFeatureMessageProcessor()

        val stringMessage = FeatureStringMessage("Test string message")
        val eventMessage = TestFeatureEventMessage("Test event message")

        val messagesToProcess = listOf(stringMessage, eventMessage)
        messagesToProcess.forEach { message -> processor.onMessage(message) }

        val expectedMessages = listOf(stringMessage, eventMessage)

        assertEquals(expectedMessages.size, processor.processedMessages.size)
        assertContentEquals(expectedMessages, processor.processedMessages)
    }

    @Test
    @JsName("testFilterMessagesOnMessagesProcessor")
    fun `test filter events on messages processor`() = runTest {
        val processor = TestFeatureMessageProcessor()
        processor.setMessageFilter { message ->
            message is TestFeatureEventMessage
        }

        val stringMessage = FeatureStringMessage("Test string message")
        val eventMessage = TestFeatureEventMessage("Test event message")

        val messagesToProcess = listOf(stringMessage, eventMessage)
        messagesToProcess.forEach { message -> processor.onMessage(message) }

        val expectedMessages = listOf(eventMessage)

        assertEquals(expectedMessages.size, processor.processedMessages.size)
        assertContentEquals(expectedMessages, processor.processedMessages)
    }

    //endregion Filter
}
