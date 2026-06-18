package ai.koog.agents.cli.transport

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertTrue

class ProcessCliTransportTest {
    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    @Test
    fun testCheckAvailability() = runTest {
        val availability = CliTransport.LocalProcess.checkAvailability("java", ".")
        availability.shouldBeInstanceOf<CliAvailable>()
    }

    @Test
    fun testExecuteEcho() = runTest {
        val events = CliTransport.LocalProcess.execute(
            command = listOf("echo", "hello world"),
            workspace = "."
        ).toList()

        events.filterIsInstance<CliEvent.Stdout>()
            .firstOrNull()
            .shouldNotBeNull()
            .content
            .trim()
            .trim('"')
            .shouldBe("hello world")

        events.last()
            .shouldBeInstanceOf<CliEvent.Exit>()
            .code.shouldBe(0)
    }

    @Test
    fun testExecuteInvalidCommand() = runTest {
        assertThrows<Exception> {
            val events = CliTransport.LocalProcess.execute(
                command = listOf("non-existent-command-12345"),
                workspace = "."
            ).toList()

            val exitEvent = events.filterIsInstance<CliEvent.Exit>().firstOrNull()
            if (exitEvent?.code == 127 || (isWindows && exitEvent?.code == 1)) {
                throw Exception("Command not found")
            }
        }
    }

    @Test
    fun testExecuteWithEnv() = runTest {
        val env = mapOf("TEST_VAR" to "test-value")

        val command = if (isWindows) {
            listOf("echo", "%TEST_VAR%")
        } else {
            listOf("sh", "-c", "echo \$TEST_VAR")
        }

        val events = CliTransport.LocalProcess.execute(
            command = command,
            workspace = ".",
            env = env
        ).toList()

        events
            .filterIsInstance<CliEvent.Stdout>()
            .firstOrNull()
            .shouldNotBeNull()
            .content.shouldBe("test-value")
    }

    @Test
    fun testExecuteStderr() = runTest {
        val command = if (isWindows) {
            listOf("echo error message 1>&2")
        } else {
            listOf("sh", "-c", "echo 'error message' >&2")
        }

        val events = CliTransport.LocalProcess.execute(
            command = command,
            workspace = "."
        ).toList()

        assertTrue(
            events
                .filterIsInstance<CliEvent.Stderr>()
                .any { it.content.contains("error message") },
            "error message should be captured from the stderr"
        )
    }
}
