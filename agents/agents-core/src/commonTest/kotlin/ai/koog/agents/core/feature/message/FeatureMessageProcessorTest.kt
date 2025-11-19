package ai.koog.agents.core.feature.message

import ai.koog.agents.core.feature.mock.TestFeatureEventMessage
import ai.koog.agents.core.feature.mock.TestFeatureMessageProcessor
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.FeatureStringMessage
import ai.koog.agents.core.feature.model.events.NodeExecutionCompletedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionFailedEvent
import ai.koog.agents.core.feature.model.events.NodeExecutionStartingEvent
import ai.koog.utils.io.use
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.JsName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureMessageProcessorTest {

    private val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

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

    //region Node Events

    @Test
    @JsName("testNodeExecutionStartingEvenWithStringInputParameter")
    fun `test node execution starting even with string input parameter`() = runTest {
        TestFeatureMessageProcessor().use { processor ->

            val testRunId = "test-run-id"
            val testNodeName = "test-node"
            val testInput = "Test input"

            val nodeExecutionStartingEvent = NodeExecutionStartingEvent(
                runId = testRunId,
                nodeName = testNodeName,
                input = testInput,
                timestamp = testClock.now().toEpochMilliseconds()
            )

            processor.onMessage(nodeExecutionStartingEvent)

            // Verify messages
            val expectedMessages = listOf(
                NodeExecutionStartingEvent(
                    runId = testRunId,
                    nodeName = testNodeName,
                    input = JsonPrimitive(testInput),
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedMessages.size, processor.processedMessages.size)
            assertContentEquals(expectedMessages, processor.processedMessages)
        }
    }

    @Test
    @JsName("testNodeExecutionCompletedEvenWithStringInputAndOutputParameters")
    fun `test node execution completed even with string input and output parameters`() = runTest {
        TestFeatureMessageProcessor().use { processor ->

            val testRunId = "test-run-id"
            val testNodeName = "test-node"
            val testInput = "Test input"
            val testOutput = "Test output"

            val nodeExecutionCompletedEvent = NodeExecutionCompletedEvent(
                runId = testRunId,
                nodeName = testNodeName,
                input = testInput,
                output = testOutput,
                timestamp = testClock.now().toEpochMilliseconds()
            )

            processor.onMessage(nodeExecutionCompletedEvent)

            val expectedMessages = listOf(
                NodeExecutionCompletedEvent(
                    runId = testRunId,
                    nodeName = testNodeName,
                    input = JsonPrimitive(testInput),
                    output = JsonPrimitive(testOutput),
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedMessages.size, processor.processedMessages.size)
            assertContentEquals(expectedMessages, processor.processedMessages)
        }
    }

    @Test
    @JsName("testNodeExecutionFailedEvenWithoutInputParameter")
    fun `test node execution failed even without input parameter`() = runTest {
        TestFeatureMessageProcessor().use { processor ->

            val testRunId = "test-run-id"
            val testNodeName = "test-node"
            val testError = AIAgentError(
                message = "Test error message",
                stackTrace = "Test stack trace",
                cause = "Test cause"
            )

            // Node Execution Failed Event
            val nodeExecutionFailedEvent = NodeExecutionFailedEvent(
                runId = testRunId,
                nodeName = testNodeName,
                error = testError,
                timestamp = testClock.now().toEpochMilliseconds()
            )

            processor.onMessage(nodeExecutionFailedEvent)

            val expectedMessages = listOf(
                NodeExecutionFailedEvent(
                    runId = testRunId,
                    nodeName = testNodeName,
                    input = null,
                    error = testError,
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedMessages.size, processor.processedMessages.size)
            assertContentEquals(expectedMessages, processor.processedMessages)
        }
    }

    @Test
    @JsName("testNodeExecutionStartingEvenWithJsonInputParameter")
    fun `test node execution starting even with json input parameter`() = runTest {
        TestFeatureMessageProcessor().use { processor ->

            val testRunId = "test-run-id"
            val testNodeName = "test-node"
            val testInput = JsonPrimitive("Test input")

            val nodeExecutionStartingEvent = NodeExecutionStartingEvent(
                runId = testRunId,
                nodeName = testNodeName,
                input = testInput,
                timestamp = testClock.now().toEpochMilliseconds()
            )

            processor.onMessage(nodeExecutionStartingEvent)

            val expectedMessages = listOf(
                NodeExecutionStartingEvent(
                    runId = testRunId,
                    nodeName = testNodeName,
                    input = testInput,
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedMessages.size, processor.processedMessages.size)
            assertContentEquals(expectedMessages, processor.processedMessages)
        }
    }

    @Test
    @JsName("testNodeExecutionCompletedEvenWithJsonInputAndOutputParameters")
    fun `test node execution completed even with json input and output parameters`() = runTest {
        TestFeatureMessageProcessor().use { processor ->

            val testRunId = "test-run-id"
            val testNodeName = "test-node"
            val testInput = JsonPrimitive("Test input")
            val testOutput = JsonPrimitive("Test output")

            val nodeExecutionCompletedEvent = NodeExecutionCompletedEvent(
                runId = testRunId,
                nodeName = testNodeName,
                input = testInput,
                output = testOutput,
                timestamp = testClock.now().toEpochMilliseconds()
            )

            processor.onMessage(nodeExecutionCompletedEvent)

            val expectedMessages = listOf(
                NodeExecutionCompletedEvent(
                    runId = testRunId,
                    nodeName = testNodeName,
                    input = testInput,
                    output = testOutput,
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedMessages.size, processor.processedMessages.size)
            assertContentEquals(expectedMessages, processor.processedMessages)
        }
    }

    @Test
    @JsName("testNodeExecutionFailedEvenWithInputParameter")
    fun `test node execution failed even with input parameter`() = runTest {
        TestFeatureMessageProcessor().use { processor ->

            val testRunId = "test-run-id"
            val testNodeName = "test-node"
            val testInput = JsonPrimitive("Test input")
            val testError = AIAgentError(
                message = "Test error message",
                stackTrace = "Test stack trace",
                cause = "Test cause"
            )

            // Node Execution Failed Event
            val nodeExecutionFailedEvent = NodeExecutionFailedEvent(
                runId = testRunId,
                nodeName = testNodeName,
                input = testInput,
                error = testError,
                timestamp = testClock.now().toEpochMilliseconds()
            )

            processor.onMessage(nodeExecutionFailedEvent)

            val expectedMessages = listOf(
                NodeExecutionFailedEvent(
                    runId = testRunId,
                    nodeName = testNodeName,
                    input = testInput,
                    error = testError,
                    timestamp = testClock.now().toEpochMilliseconds()
                )
            )

            assertEquals(expectedMessages.size, processor.processedMessages.size)
            assertContentEquals(expectedMessages, processor.processedMessages)
        }
    }

    //endregion Node Events
}
