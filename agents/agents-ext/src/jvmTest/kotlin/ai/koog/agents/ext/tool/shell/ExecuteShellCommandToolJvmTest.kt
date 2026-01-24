package ai.koog.agents.ext.tool.shell

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(InternalAgentToolsApi::class)
class ExecuteShellCommandToolJvmTest {

    private val executor = JvmShellCommandExecutor()

    @TempDir
    lateinit var tempDir: Path

    private suspend fun ExecuteShellCommandTool.executeShellCommand(
        command: String,
        timeoutSeconds: Int = 60,
        workingDirectory: String? = null,
    ): ExecuteShellCommandTool.Result {
        return execute(
            ExecuteShellCommandTool.Args(command, timeoutSeconds, workingDirectory)
        )
    }

    @Test
    fun `args defaults`() {
        val args = ExecuteShellCommandTool.Args("echo hello", 60)

        assertEquals("echo hello", args.command)
        assertNull(args.workingDirectory)
        assertEquals(60, args.timeoutSeconds)
    }

    @Test
    fun `descriptor configuration`() {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val descriptor = tool.descriptor

        assertEquals("__execute_shell_command__", descriptor.name)
        assertEquals(listOf("command", "timeoutSeconds"), descriptor.requiredParameters.map { it.name })
        assertEquals(listOf("workingDirectory"), descriptor.optionalParameters.map { it.name })
    }

    // SUCCESSFUL COMMAND EXECUTION TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reading file content and filtering with grep`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val file = tempDir.resolve("fruits.txt").createFile()
        file.writeText("apple\nbanana\napricot\ncherry\navocado")

        val result = tool.executeShellCommand("grep ^a fruits.txt", workingDirectory = tempDir.toString())

        val expected = """
            apple
            apricot
            avocado
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `reading file content and filtering with findstr`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val file = tempDir.resolve("fruits.txt").createFile()
        file.writeText("apple\r\nbanana\r\napricot\r\ncherry\r\navocado")

        val result = tool.executeShellCommand("findstr /B a fruits.txt", workingDirectory = tempDir.toString())

        val expected = """
            apple
            apricot
            avocado
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `finding files by pattern`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        tempDir.resolve("report.txt").createFile()
        tempDir.resolve("data.json").createFile()
        tempDir.resolve("config.txt").createFile()
        tempDir.resolve("readme.md").createFile()

        val result = tool.executeShellCommand("find . -name '*.txt' -type f | sort", workingDirectory = tempDir.toString())

        val expected = """
            ./config.txt
            ./report.txt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `finding files by pattern on Windows`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        tempDir.resolve("report.txt").createFile()
        tempDir.resolve("data.json").createFile()
        tempDir.resolve("config.txt").createFile()
        tempDir.resolve("readme.md").createFile()

        val result = tool.executeShellCommand("dir /b *.txt | sort", workingDirectory = tempDir.toString())

        val expected = """
            config.txt
            report.txt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `counting lines in file on Windows`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        tempDir.resolve("file.txt").writeText("line1\r\nline2\r\nline3\r\nline4")

        val result = tool.executeShellCommand("find /c /v \"\" file.txt", workingDirectory = tempDir.toString())

        val expected = """

            ---------- FILE.TXT: 4
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `listing directory structure`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val subDir = tempDir.resolve("src/main").createDirectories()
        subDir.resolve("App.kt").createFile()
        subDir.resolve("Utils.kt").createFile()
        tempDir.resolve("README.md").createFile()

        val result = tool.executeShellCommand("find . -type f | sort", workingDirectory = tempDir.toString())

        val expected = """
            ./README.md
            ./src/main/App.kt
            ./src/main/Utils.kt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `listing directory structure on Windows`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val subDir = tempDir.resolve("src\\main").createDirectories()
        subDir.resolve("App.kt").createFile()
        subDir.resolve("Utils.kt").createFile()
        tempDir.resolve("README.md").createFile()

        val result = tool.executeShellCommand("dir /s /b /o:n", workingDirectory = tempDir.toString())

        val tempDirStr = tempDir.toAbsolutePath().toString()

        val expected = """
            $tempDirStr\README.md
            $tempDirStr\src
            $tempDirStr\src\main
            $tempDirStr\src\main\App.kt
            $tempDirStr\src\main\Utils.kt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    // NO OUTPUT COMMAND EXECUTION TESTS

    @Test
    fun `command with no output shows placeholder`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val testDir = tempDir.resolve("empty_test").createDirectories()
        val result = tool.executeShellCommand("mkdir newdir", workingDirectory = testDir.toString())

        val expected = """
            (no output)
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
        assertEquals(0, result.exitCode)
    }

    // COMMAND FAILURE TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `command fails with error message`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val result = tool.executeShellCommand("grep nonexistent /nonexistent/file.txt")

        val expected = """
            grep: /nonexistent/file.txt: No such file or directory
            Exit code: 2
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `command fails with error message on Windows`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val result = tool.executeShellCommand("type C:\\nonexistent\\file.txt")

        val expected = """
            The system cannot find the path specified.
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `stdout and stderr are both captured`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        tempDir.resolve("file1.txt").writeText("Hello from file1")

        val result = tool.executeShellCommand("cat file1.txt file2.txt", workingDirectory = tempDir.toString())

        val expected = """
            Hello from file1
            cat: file2.txt: No such file or directory
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `stdout and stderr are both captured on Windows`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        tempDir.resolve("file1.txt").writeText("Hello from file1")

        val result = tool.executeShellCommand("type file1.txt file2.txt", workingDirectory = tempDir.toString())

        val expected = """
            Hello from file1

            file1.txt


            The system cannot find the file specified.
            Error occurred while processing: file2.txt.
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
    }

    // USER DENIAL TESTS

    @Test
    fun `user denies command execution with simple No`() = runBlocking {
        val handler = ShellCommandConfirmationHandler { ShellCommandConfirmation.Denied("No") }
        val tool = ExecuteShellCommandTool(executor, confirmationHandler = handler)

        val result = tool.executeShellCommand("rm important-file.txt",)

        val expected = """
            Command execution denied with user response: No
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
        assertNull(result.exitCode)
    }

    @Test
    fun `user denies with reason`() = runBlocking {
        val handler = ShellCommandConfirmationHandler { ShellCommandConfirmation.Denied("Cannot delete important files") }
        val tool = ExecuteShellCommandTool(executor, confirmationHandler = handler)

        val result = tool.executeShellCommand("rm important-file.txt")

        val expected = """
            Command execution denied with user response: Cannot delete important files
        """.trimIndent()

        assertEquals(expected, tool.encodeResultToString(result))
        assertNull(result.exitCode)
    }

    // TIMEOUT  TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `command with partial output times out`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())

        val result: ExecuteShellCommandTool.Result
        val executionTimeMs = measureTimeMillis {
            result = withTimeout(4000L) {
                tool.executeShellCommand(
                    "echo beforeSleep && sleep 10 && echo afterSleep",
                    timeoutSeconds = 1
                )
            }
        }

        val partialExpected = """
        beforeSleep
        """.trimIndent()

        val output = tool.encodeResultToString(result)
        assertTrue(output.contains(partialExpected), "Partial output not found. Actual: $output")
        assertTrue(output.contains("Command timed out after 1 seconds"), "Timeout message not found. Actual: $output")

        // We have to remove the command because it DOES contain afterSleep
        assertFalse(
            output.replace(partialExpected, "").contains("afterSleep"),
            "afterSleep should not appear since command timed out"
        )

        assertNull(result.exitCode, "Exit code should be null for timed out command")
        assertTrue(executionTimeMs < 3000, "Should timeout at 1s, but took ${executionTimeMs}ms")
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `command with partial output times out on Windows`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())

        val result: ExecuteShellCommandTool.Result
        val executionTimeMs = measureTimeMillis {
            result = withTimeout(5000L) {
                tool.executeShellCommand(
                    """
                    powershell -Command "'beforeSleep'; Start-Sleep -Seconds 10; 'afterSleep'"
                    """.trimIndent(),
                    timeoutSeconds = 1
                )
            }
        }

        val partialExpected = """
        """.trimIndent()

        val output = tool.encodeResultToString(result)
        assertTrue(output.contains(partialExpected), "Partial output not found. Actual: $output")
        assertTrue(output.contains("Command timed out after 1 seconds"), "Timeout message not found. Actual: $output")

        // We have to remove the command because it DOES contain afterSleep
        assertFalse(
            output.replace(partialExpected, "").contains("afterSleep"),
            "afterSleep should not appear since command timed out"
        )

        assertNull(result.exitCode, "Exit code should be null for timed out command")
        assertTrue(executionTimeMs < 4000, "Should timeout at 1s, but took ${executionTimeMs}ms")
    }

    // CANCELLATION TESTS

    @RepeatedTest(10)
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `executor can be cancelled with timeout`() = runBlocking {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())

        val timeoutSeconds = 1

        val job = launch {
            val result = tool.executeShellCommand("sleep 1.1", timeoutSeconds = timeoutSeconds)
            fail("Command should have been cancelled, but completed with: $result")
        }

        delay(10)

        val cancelDurationMs = measureTimeMillis {
            job.cancelAndJoin()
        }

        assertTrue(
            cancelDurationMs < timeoutSeconds * 500, // 2 times faster than timeout
            "Cancellation should happen relatively fast, at least less than timeout and the actual command, but took ${cancelDurationMs}ms"
        )
        assertTrue(job.isCancelled, "Job should be cancelled")
    }
}
