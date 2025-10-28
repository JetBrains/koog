package ai.koog.agents.ext.tool.shell

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@OptIn(InternalAgentToolsApi::class)
class ExecuteShellCommandToolJvmTest {

    private val executor = JvmShellCommandExecutor()

    @TempDir
    lateinit var tempDir: Path

    private suspend fun executeShellCommand(
        command: String,
        timeoutSeconds: Int = 60,
        workingDirectory: String? = null,
        confirmationHandler: ShellCommandConfirmationHandler = BraveModeConfirmationHandler()
    ): ExecuteShellCommandTool.Result {
        return ExecuteShellCommandTool(executor, confirmationHandler).execute(
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
        val file = tempDir.resolve("fruits.txt").createFile()
        file.writeText("apple\nbanana\napricot\ncherry\navocado")

        val result = executeShellCommand("grep ^a fruits.txt", workingDirectory = tempDir.toString())

        val expected = """
            Command: grep ^a fruits.txt
            apple
            apricot
            avocado
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `reading file content and filtering with findstr`() = runBlocking {
        val file = tempDir.resolve("fruits.txt").createFile()
        file.writeText("apple\r\nbanana\r\napricot\r\ncherry\r\navocado")

        val result = executeShellCommand("findstr /B a fruits.txt", workingDirectory = tempDir.toString())

        val expected = """
            Command: findstr /B a fruits.txt
            apple
            apricot
            avocado
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `finding files by pattern`() = runBlocking {
        tempDir.resolve("report.txt").createFile()
        tempDir.resolve("data.json").createFile()
        tempDir.resolve("config.txt").createFile()
        tempDir.resolve("readme.md").createFile()

        val result = executeShellCommand("find . -name '*.txt' -type f | sort", workingDirectory = tempDir.toString())

        val expected = """
            Command: find . -name '*.txt' -type f | sort
            ./config.txt
            ./report.txt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `finding files by pattern on Windows`() = runBlocking {
        tempDir.resolve("report.txt").createFile()
        tempDir.resolve("data.json").createFile()
        tempDir.resolve("config.txt").createFile()
        tempDir.resolve("readme.md").createFile()

        val result = executeShellCommand("dir /b *.txt | sort", workingDirectory = tempDir.toString())

        val expected = """
            Command: dir /b *.txt | sort
            config.txt
            report.txt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `counting lines in file on Windows`() = runBlocking {
        tempDir.resolve("file.txt").writeText("line1\r\nline2\r\nline3\r\nline4")

        val result = executeShellCommand("find /c /v \"\" file.txt", workingDirectory = tempDir.toString())

        val expected = """
            Command: find /c /v "" file.txt
            
            ---------- FILE.TXT: 4
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `listing directory structure`() = runBlocking {
        val subDir = tempDir.resolve("src/main").createDirectories()
        subDir.resolve("App.kt").createFile()
        subDir.resolve("Utils.kt").createFile()
        tempDir.resolve("README.md").createFile()

        val result = executeShellCommand("find . -type f | sort", workingDirectory = tempDir.toString())

        val expected = """
            Command: find . -type f | sort
            ./README.md
            ./src/main/App.kt
            ./src/main/Utils.kt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `listing directory structure on Windows`() = runBlocking {
        val subDir = tempDir.resolve("src\\main").createDirectories()
        subDir.resolve("App.kt").createFile()
        subDir.resolve("Utils.kt").createFile()
        tempDir.resolve("README.md").createFile()

        val result = executeShellCommand("dir /s /b /o:n", workingDirectory = tempDir.toString())

        val tempDirStr = tempDir.toAbsolutePath().toString()

        val expected = """
            Command: dir /s /b /o:n
            $tempDirStr\README.md
            $tempDirStr\src
            $tempDirStr\src\main
            $tempDirStr\src\main\App.kt
            $tempDirStr\src\main\Utils.kt
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    // NO OUTPUT COMMAND EXECUTION TESTS

    @Test
    fun `command with no output shows placeholder`() = runBlocking {
        val testDir = tempDir.resolve("empty_test").createDirectories()
        val result = executeShellCommand("mkdir newdir", workingDirectory = testDir.toString())

        val expected = """
            Command: mkdir newdir
            (no output)
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertEquals(0, result.exitCode)
    }

    // COMMAND FAILURE TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `command fails with error message`() = runBlocking {
        val result = executeShellCommand("grep nonexistent /nonexistent/file.txt")

        val expected = """
            Command: grep nonexistent /nonexistent/file.txt
            grep: /nonexistent/file.txt: No such file or directory
            Exit code: 2
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `command fails with error message on Windows`() = runBlocking {
        val result = executeShellCommand("type C:\\nonexistent\\file.txt")

        val expected = """
            Command: type C:\nonexistent\file.txt
            The system cannot find the path specified.
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `stdout and stderr are both captured`() = runBlocking {
        tempDir.resolve("file1.txt").writeText("Hello from file1")

        val result = executeShellCommand("cat file1.txt file2.txt", workingDirectory = tempDir.toString())

        val expected = """
            Command: cat file1.txt file2.txt
            Hello from file1
            cat: file2.txt: No such file or directory
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `stdout and stderr are both captured on Windows`() = runBlocking {
        tempDir.resolve("file1.txt").writeText("Hello from file1")

        val result = executeShellCommand("type file1.txt file2.txt", workingDirectory = tempDir.toString())

        val expected = """
            Command: type file1.txt file2.txt
            Hello from file1
            
            file1.txt
            
            
            The system cannot find the file specified.
            Error occurred while processing: file2.txt.
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    // USER DENIAL TESTS

    @Test
    fun `user denies command execution with simple No`() = runBlocking {
        val handler = ShellCommandConfirmationHandler { ShellCommandConfirmation.Denied("No") }

        val result = executeShellCommand("rm important-file.txt", confirmationHandler = handler)

        val expected = """
            Command: rm important-file.txt
            Command execution denied with user response: No
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    fun `user denies with reason`() = runBlocking {
        val handler =
            ShellCommandConfirmationHandler { ShellCommandConfirmation.Denied("Cannot delete important files") }

        val result = executeShellCommand("rm important-file.txt", confirmationHandler = handler)

        val expected = """
            Command: rm important-file.txt
            Command execution denied with user response: Cannot delete important files
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    // TIMEOUT  TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `long running command times out`() = runBlocking {
        val command = "sleep 5"
        val timeout = 1
        val result = executeShellCommand(command, timeoutSeconds = timeout)

        val expected = """
            Command: $command
            Command timed out after $timeout seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `long running command times out on Windows`() = runBlocking {
        val result = executeShellCommand("powershell -Command \"Start-Sleep -Milliseconds 1100\"", timeoutSeconds = 1)

        val expected = """
            Command: powershell -Command "Start-Sleep -Milliseconds 1100"
            Command timed out after 1 seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `command with partial output times out`() = runBlocking {
        val result = executeShellCommand("for i in {1..3}; do echo \$i; sleep 0.5; done", timeoutSeconds = 1)

        assertTrue(result.textForLLM().contains("Command timed out after 1 seconds"))
        assertTrue(result.textForLLM().startsWith("Command: for i in {1..3}; do echo \$i; sleep 0.5; done"))
        assertNull(result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `command with partial output times out on Windows`() = runBlocking {
        val result = executeShellCommand(
            "powershell -Command \"1..3 | ForEach-Object { Write-Output \$_; Start-Sleep -Milliseconds 500 }\"",
            timeoutSeconds = 1
        )

        assertTrue(result.textForLLM().contains("Command timed out after 1 seconds"))
        assertTrue(
            result.textForLLM()
                .startsWith("Command: powershell -Command \"1..3 | ForEach-Object { Write-Output \$_; Start-Sleep -Milliseconds 500 }\"")
        )
        assertNull(result.exitCode)
    }

    // CANCELLATION TESTS

    @RepeatedTest(10)
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `executor can be cancelled with timeout`() = runBlocking {
        val timeoutSeconds = 1

        val job = launch {
            val result = executeShellCommand("sleep 1.1", timeoutSeconds = timeoutSeconds)
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
