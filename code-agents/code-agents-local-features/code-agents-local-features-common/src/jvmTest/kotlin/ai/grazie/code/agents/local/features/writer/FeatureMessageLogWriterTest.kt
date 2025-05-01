package ai.grazie.code.agents.local.features.writer

import ai.grazie.code.agents.local.features.common.writer.FeatureMessageLogWriter
import ai.grazie.code.agents.local.features.common.writer.FeatureMessageLogWriter.LogLevel
import ai.grazie.code.agents.local.features.message.FeatureEvent
import ai.grazie.code.agents.local.features.message.FeatureMessage
import ai.grazie.code.agents.local.features.message.FeatureStringMessage
import ai.grazie.code.agents.local.features.message.use
import ai.grazie.utils.mpp.MPPLogger
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureMessageLogWriterTest {

    class TestFeatureMessageLogWriter(
        targetLogger: MPPLogger,
        logLevel: LogLevel = LogLevel.INFO,
    ) : FeatureMessageLogWriter(targetLogger = targetLogger, logLevel = logLevel) {
        override fun FeatureMessage.toLoggerMessage(): String {
            return when (this) {
                is FeatureStringMessage -> {
                    "message: ${this.message}"
                }
                is FeatureEvent -> {
                    "event id: ${this.eventId}"
                }
                else -> {
                    "UNDEFINED"
                }
            }
        }
    }

    private val targetLogger = TestLogger("test-logger")

    @AfterTest
    fun resetLogger() {
        targetLogger.reset()
    }

    @Test
    fun `test logger stream feature provider for string message`() = runBlocking {
        val messages = listOf(
            FeatureStringMessage("test message - 1"),
            FeatureStringMessage("test message - 2")
        )

        TestFeatureMessageLogWriter(targetLogger).use { writer ->

            messages.forEach { message -> writer.processMessage(message) }

            val expectedLogMessages = messages.map { originalMessage ->
                "[INFO] Received feature message [${originalMessage.messageType.value}]: message: ${originalMessage.message}"
            }

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertEquals(expectedLogMessages[0], targetLogger.messages[0])
            assertEquals(expectedLogMessages[1], targetLogger.messages[1])
        }
    }

    @Test
    fun `test logger stream feature provider for event message`() = runBlocking {
        val messages = listOf(
            TestFeatureEventMessage("test-event-1"),
            TestFeatureEventMessage("test-event-2"),
        )

        TestFeatureMessageLogWriter(targetLogger).use { writer ->

            messages.forEach { message -> writer.processMessage(message) }

            val expectedLogMessages = messages.map { originalMessage ->
                "[INFO] Received feature message [${originalMessage.messageType.value}]: event id: ${originalMessage.eventId}"
            }

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertEquals(expectedLogMessages[0], targetLogger.messages[0])
            assertEquals(expectedLogMessages[1], targetLogger.messages[1])
        }
    }

    @Test
    fun `test logger stream feature provider for multiple messages`() = runBlocking {
        val messages = listOf(
            FeatureStringMessage("test message 1"),
            TestFeatureEventMessage("test event 1"),
        )

        TestFeatureMessageLogWriter(targetLogger).use { writer ->

            messages.forEach { message -> writer.processMessage(message) }

            val expectedLogMessages = listOf(
                "[INFO] Received feature message [${messages[0].messageType.value}]: message: ${(messages[0] as FeatureStringMessage).message}",
                "[INFO] Received feature message [${messages[1].messageType.value}]: event id: ${(messages[1] as TestFeatureEventMessage).eventId}"
            )

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertEquals(expectedLogMessages[0], targetLogger.messages[0])
            assertEquals(expectedLogMessages[1], targetLogger.messages[1])
        }
    }

    @Test
    fun `test logger with DEBUG log level for string message`() = runBlocking {
        val messages = listOf(
            FeatureStringMessage("debug test message - 1"),
            FeatureStringMessage("debug test message - 2")
        )

        TestFeatureMessageLogWriter(targetLogger, LogLevel.DEBUG).use { writer ->
            messages.forEach { message -> writer.processMessage(message) }

            val expectedLogMessages = messages.map { originalMessage ->
                "[DEBUG] Received feature message [${originalMessage.messageType.value}]: message: ${originalMessage.message}"
            }

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertEquals(expectedLogMessages[0], targetLogger.messages[0])
            assertEquals(expectedLogMessages[1], targetLogger.messages[1])
        }
    }

    @Test
    fun `test logger with DEBUG log level for event message`() = runBlocking {
        val messages = listOf(
            TestFeatureEventMessage("debug-test-event-1"),
            TestFeatureEventMessage("debug-test-event-2"),
        )

        TestFeatureMessageLogWriter(targetLogger, LogLevel.DEBUG).use { writer ->
            messages.forEach { message -> writer.processMessage(message) }

            val expectedLogMessages = messages.map { originalMessage ->
                "[DEBUG] Received feature message [${originalMessage.messageType.value}]: event id: ${originalMessage.eventId}"
            }

            assertEquals(expectedLogMessages.size, targetLogger.messages.size)
            assertEquals(expectedLogMessages[0], targetLogger.messages[0])
            assertEquals(expectedLogMessages[1], targetLogger.messages[1])
        }
    }

    @Test
    fun `test logger with DEBUG level when debug logging is disabled`() = runBlocking {

        val testLogger = TestLogger("test-logger", infoEnabled = true, debugEnabled = false)

        val messages = listOf(
            FeatureStringMessage("debug disabled message"),
            TestFeatureEventMessage("debug disabled event")
        )

        // Even though we set LogLevel.DEBUG, messages should be added as debug logs.
        TestFeatureMessageLogWriter(testLogger, LogLevel.DEBUG).use { writer ->
            messages.forEach { message -> writer.processMessage(message) }
            assertEquals(2, testLogger.messages.size)
        }
    }

    @Test
    fun `test logger with INFO level when info logging is disabled`() = runBlocking {

        val testLogger = TestLogger("test-logger", infoEnabled = false, debugEnabled = true)

        val messages = listOf(
            FeatureStringMessage("info disabled message"),
            TestFeatureEventMessage("info disabled event")
        )

        // Even though we set LogLevel.INFO, messages should be added as info logs.
        TestFeatureMessageLogWriter(testLogger, LogLevel.INFO).use { writer ->
            messages.forEach { message -> writer.processMessage(message) }
            assertEquals(2, testLogger.messages.size)
        }
    }
}
