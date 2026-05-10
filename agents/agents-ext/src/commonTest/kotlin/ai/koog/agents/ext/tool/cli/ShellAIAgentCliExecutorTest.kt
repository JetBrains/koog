package ai.koog.agents.ext.tool.cli

import ai.koog.agents.ext.tool.shell.ShellCommandExecutor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShellAIAgentCliExecutorTest {
    @Test
    fun testExecutesRenderedCommandThroughShellExecutor() = runTest {
        val shellExecutor = RecordingShellCommandExecutor(
            ShellCommandExecutor.ExecutionResult(output = "done", exitCode = null, timedOut = true)
        )
        val executor = ShellAIAgentCliExecutor(shellExecutor)
        val request = AIAgentCliExecutionRequest(
            profileId = "test",
            executable = "agent",
            arguments = listOf("run", "can't ; rm -rf /"),
            workingDirectory = "/repo",
            timeoutSeconds = 17
        )

        val result = executor.execute(request)

        assertEquals("""'agent' 'run' 'can'"'"'t ; rm -rf /'""", shellExecutor.lastCommand)
        assertEquals("/repo", shellExecutor.lastWorkingDirectory)
        assertEquals(17, shellExecutor.lastTimeoutSeconds)
        assertEquals("done", result.output)
        assertEquals(null, result.exitCode)
        assertTrue(result.timedOut)
    }

    @Test
    fun testRendersEnvironmentAndStdin() {
        val request = AIAgentCliExecutionRequest(
            profileId = "test",
            executable = "agent",
            arguments = listOf("run"),
            stdin = "line1\nline2",
            environment = mapOf("AGENT_MODE" to "test value"),
            timeoutSeconds = 5
        )

        val command = PosixAIAgentCliShellCommandLineRenderer.render(request)

        assertEquals("printf %s 'line1\nline2' | AGENT_MODE='test value' 'agent' 'run'", command)
    }

    @Test
    fun testRejectsInvalidEnvironmentName() {
        val request = AIAgentCliExecutionRequest(
            profileId = "test",
            executable = "agent",
            arguments = listOf("run"),
            environment = mapOf("INVALID-NAME" to "value"),
            timeoutSeconds = 5
        )

        assertFailsWith<IllegalArgumentException> {
            PosixAIAgentCliShellCommandLineRenderer.render(request)
        }
    }

    private class RecordingShellCommandExecutor(
        private val result: ShellCommandExecutor.ExecutionResult,
    ) : ShellCommandExecutor {
        lateinit var lastCommand: String
        var lastWorkingDirectory: String? = null
        var lastTimeoutSeconds: Int = 0

        override suspend fun execute(
            command: String,
            workingDirectory: String?,
            timeoutSeconds: Int,
        ): ShellCommandExecutor.ExecutionResult {
            lastCommand = command
            lastWorkingDirectory = workingDirectory
            lastTimeoutSeconds = timeoutSeconds
            return result
        }
    }
}
