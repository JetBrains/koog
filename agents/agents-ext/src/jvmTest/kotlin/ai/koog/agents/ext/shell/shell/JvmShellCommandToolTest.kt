package ai.koog.agents.ext.shell.shell

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.tool.shell.JvmShellCommandExecutor
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
        confirmationHandler: ShellCommandConfirmationHandler = AlwaysApproveConfirmationHandler()
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
        val tool = ExecuteShellCommandTool(executor, AlwaysApproveConfirmationHandler())
        val descriptor = tool.descriptor

        assertEquals("__execute_shell_command__", descriptor.name)
        assertEquals(listOf("command"), descriptor.requiredParameters.map { it.name })
        assertEquals(listOf("workingDirectory", "timeoutSeconds"), descriptor.optionalParameters.map { it.name })
    }

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
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `counting lines in multiple files`() = runBlocking {
        tempDir.resolve("file1.txt").writeText("line1\nline2\nline3\n")
        tempDir.resolve("file2.txt").writeText("line1\nline2\n")

        val result = execute("wc -l file1.txt file2.txt", workingDirectory = tempDir.toString())

        val expected = """
            Command: wc -l file1.txt file2.txt
                   3 file1.txt
                   2 file2.txt
                   5 total
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

        val tempDirStr = tempDir.toString().replace("\\", "\\\\")

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
            The system cannot find the file specified.
            Exit code: 1
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `working in subdirectory`() = runBlocking {
        val projectDir = tempDir.resolve("project").createDirectories()
        projectDir.resolve("package.json").writeText("""{"name": "my-app"}""")
        projectDir.resolve("index.js").writeText("console.log('hello')")

        val result = execute("ls -1", workingDirectory = projectDir.toString())

        val expected = """
            Command: ls -1
            index.js
            package.json
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `working in subdirectory on Windows`() = runBlocking {
        val projectDir = tempDir.resolve("project").createDirectories()
        projectDir.resolve("package.json").writeText("""{"name": "my-app"}""")
        projectDir.resolve("index.js").writeText("console.log('hello')")

        val result = execute("dir /b /o:n", workingDirectory = projectDir.toString())

        val expected = """
            Command: dir /b /o:n
            index.js
            package.json
            Exit code: 0
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
    }

    @Test
    fun `user denies command execution`() = runBlocking {
        val handler = object : ShellCommandConfirmationHandler {
            override suspend fun requestConfirmation(command: String, workingDirectory: String?) =
                ShellCommandConfirmation.Denied
        }

        val result = execute("rm important-file.txt", confirmationHandler = handler)

        val expected = """
            Command: rm important-file.txt
            Command execution denied by user
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

    @Test
    fun `user denies with reason`() = runBlocking {
        val handler = object : ShellCommandConfirmationHandler {
            override suspend fun requestConfirmation(command: String, workingDirectory: String?) =
                ShellCommandConfirmation.DeniedWithReason("Cannot delete important files")
        }

        val result = execute("rm important-file.txt", confirmationHandler = handler)

        val expected = """
            Command: rm important-file.txt
            Command execution denied: Cannot delete important files
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }

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
        val result = execute("timeout /t 10 /nobreak", timeoutSeconds = 1)

        val expected = """
            Command: timeout /t 10 /nobreak
            Command timed out after 1 seconds
        """.trimIndent()

        assertEquals(expected, result.textForLLM())
        assertNull(result.exitCode)
    }
}
