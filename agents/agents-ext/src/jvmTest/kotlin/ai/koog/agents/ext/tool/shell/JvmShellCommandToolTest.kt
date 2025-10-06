package ai.koog.agents.ext.tool.shell

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class JvmShellCommandToolTest {

    @OptIn(InternalAgentToolsApi::class)
    private val enabler = object : DirectToolCallsEnabler {}

    private val executor = JvmShellCommandExecutor()

    @TempDir
    lateinit var tempDir: Path

    private suspend fun execute(
        command: String,
        workingDirectory: String? = null,
        timeoutSeconds: Int = 60,
        confirmationHandler: ShellCommandConfirmationHandler = BraveModeConfirmationHandler()
    ): ExecuteShellCommandTool.Result {
        val tool = ExecuteShellCommandTool(executor, confirmationHandler)
        return tool.execute(
            ExecuteShellCommandTool.Args(command, workingDirectory, timeoutSeconds),
            enabler
        )
    }

    @Test
    fun `args defaults`() {
        val args = ExecuteShellCommandTool.Args("echo hello")

        assertEquals("echo hello", args.command)
        assertNull(args.workingDirectory)
        assertEquals(60, args.timeoutSeconds)
    }

    @Test
    fun `descriptor configuration`() {
        val tool = ExecuteShellCommandTool(executor, BraveModeConfirmationHandler())
        val descriptor = tool.descriptor

        assertEquals("__execute_shell_command__", descriptor.name)
        assertEquals(listOf("command"), descriptor.requiredParameters.map { it.name })
        assertEquals(listOf("workingDirectory", "timeoutSeconds"), descriptor.optionalParameters.map { it.name })
    }

    // SUCCESSFUL COMMAND EXECUTION TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reading file content and filtering with grep`() = runBlocking {
        val file = tempDir.resolve("fruits.txt").createFile()
        file.writeText("apple\nbanana\napricot\ncherry\navocado")

        val result = execute("grep ^a fruits.txt", workingDirectory = tempDir.toString())

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

        val result = execute("findstr /B a fruits.txt", workingDirectory = tempDir.toString())

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

        val result = execute("find . -name '*.txt' -type f | sort", workingDirectory = tempDir.toString())

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

        val result = execute("dir /b *.txt | sort", workingDirectory = tempDir.toString())

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

        val result = execute("find /c /v \"\" file.txt", workingDirectory = tempDir.toString())

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

        val result = execute("find . -type f | sort", workingDirectory = tempDir.toString())

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

        val result = execute("dir /s /b /o:n", workingDirectory = tempDir.toString())

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
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `command with no output shows placeholder`() = runBlocking {
        val testDir = tempDir.resolve("empty_test").createDirectories()

        val result = execute("mkdir newdir", workingDirectory = testDir.toString())

        val expected = """
            Command: mkdir newdir
            (no output)
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertEquals(0, result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `command with no output shows placeholder on Windows`() = runBlocking {
        val testDir = tempDir.resolve("empty_test").createDirectories()

        val result = execute("mkdir newdir", workingDirectory = testDir.toString())

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
        val result = execute("grep nonexistent /nonexistent/file.txt")

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
        val result = execute("type C:\\nonexistent\\file.txt")

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

        val result = execute("cat file1.txt file2.txt", workingDirectory = tempDir.toString())

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

        val result = execute("type file1.txt file2.txt", workingDirectory = tempDir.toString())

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
        val handler = object : ShellCommandConfirmationHandler {
            override suspend fun requestConfirmation(args: ExecuteShellCommandTool.Args) =
                ShellCommandConfirmation.Denied("No")
        }

        val result = execute("rm important-file.txt", confirmationHandler = handler)

        val expected = """
            Command: rm important-file.txt
            Command execution denied with user response: No
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    fun `user denies with reason`() = runBlocking {
        val handler = object : ShellCommandConfirmationHandler {
            override suspend fun requestConfirmation(args: ExecuteShellCommandTool.Args) =
                ShellCommandConfirmation.Denied("Cannot delete important files")
        }

        val result = execute("rm important-file.txt", confirmationHandler = handler)

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
        val result = execute("sleep 10", timeoutSeconds = 1)

        val expected = """
            Command: sleep 10
            Command timed out after 1 seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `long running command times out on Windows`() = runBlocking {
        val result = execute("powershell -Command \"Start-Sleep -Seconds 10\"", timeoutSeconds = 1)

        val expected = """
            Command: powershell -Command "Start-Sleep -Seconds 10"
            Command timed out after 1 seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `command with partial output times out`() = runBlocking {
        val result = execute("for i in {1..10}; do echo \$i; sleep 1; done", timeoutSeconds = 3)

        val expected = """
            Command: for i in {1..10}; do echo ${'$'}i; sleep 1; done
            1
            2
            3
            Command timed out after 3 seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `command with partial output times out on Windows`() = runBlocking {
        val result = execute(
            """cmd /c "echo 1 & echo 2 & echo 3 & powershell -Command Start-Sleep -Seconds 10"""",
            timeoutSeconds = 1
        )

        val expected = """
            Command: cmd /c "echo 1 & echo 2 & echo 3 & powershell -Command Start-Sleep -Seconds 10"
            1 
            2 
            3
            Command timed out after 1 seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    // CANCELLATION TESTS

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `executor can be cancelled with high timeout`() = runBlocking {
        val startTime = System.currentTimeMillis()

        val job = launch {
            execute("sleep 3", null, 999)
        }

        delay(200)
        job.cancel()
        val elapsedMs = System.currentTimeMillis() - startTime

        assertTrue(elapsedMs < 1000, "Should cancel quickly, but took ${elapsedMs}ms")
        assertTrue(job.isCancelled, "Job should be cancelled")
    }
}
