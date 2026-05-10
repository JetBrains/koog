package ai.koog.agents.ext.tool.cli

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createFile
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmAIAgentCliExecutorTest {
    private val executor = JvmAIAgentCliExecutor()

    @TempDir
    lateinit var tempDir: Path

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun testExecutesCommandWithWorkingDirectoryAndStdin() = runBlocking {
        tempDir.resolve("marker.txt").createFile()
        val request = AIAgentCliExecutionRequest(
            profileId = "test",
            executable = "/bin/sh",
            arguments = listOf("-c", "read line; printf '%s:' \"${'$'}line\"; pwd; ls marker.txt"),
            stdin = "hello\n",
            workingDirectory = tempDir.toString(),
            timeoutSeconds = 5
        )

        val result = executor.execute(request)

        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("hello:"))
        assertTrue(result.output.contains("marker.txt"))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun testReportsTimeout() = runBlocking {
        val request = AIAgentCliExecutionRequest(
            profileId = "test",
            executable = "/bin/sh",
            arguments = listOf("-c", "sleep 2"),
            timeoutSeconds = 1
        )

        val result = executor.execute(request)

        assertNull(result.exitCode)
        assertTrue(result.timedOut)
        assertTrue(result.output.contains("CLI process timed out after 1 seconds"))
    }
}
