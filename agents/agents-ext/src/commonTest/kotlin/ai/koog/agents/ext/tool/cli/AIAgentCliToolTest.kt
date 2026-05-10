package ai.koog.agents.ext.tool.cli

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AIAgentCliToolTest {
    @Test
    fun testToolDelegatesTaskWithContext() = runTest {
        val executor = RecordingExecutor(
            AIAgentCliExecutionResult(output = "raw output", exitCode = 0)
        )
        val confirmationHandler = RecordingConfirmationHandler(AIAgentCliConfirmation.Approved)
        val profile = AIAgentCliProfiles.custom(
            id = "test-cli",
            displayName = "Test CLI",
            executable = "agent",
            defaultTimeoutSeconds = 30,
            argumentBuilder = AIAgentCliArgumentBuilder { task, _ ->
                AIAgentCliInvocation(
                    arguments = listOf("--task", task),
                    workingDirectory = "/profile",
                    environment = mapOf("MODE" to "test")
                )
            },
            outputExtractor = AIAgentCliOutputExtractor { "extracted: ${it.output}" }
        )
        val tool = AIAgentCliTool(profile, executor, confirmationHandler)

        val result = tool.execute(
            AIAgentCliTool.Args(
                task = "Fix the parser",
                context = "Prefer small changes.",
                workingDirectory = "/repo",
                timeoutSeconds = 12
            )
        )

        val expectedTask = """
            Fix the parser

            Additional context:
            Prefer small changes.
        """.trimIndent()

        assertEquals(1, executor.calls)
        assertEquals(listOf("agent", "--task", expectedTask), executor.lastRequest.command)
        assertEquals("/repo", executor.lastRequest.workingDirectory)
        assertEquals(mapOf("MODE" to "test"), executor.lastRequest.environment)
        assertEquals(12, executor.lastRequest.timeoutSeconds)
        assertEquals(expectedTask, confirmationHandler.lastArgs.taskWithContext)
        assertEquals("extracted: raw output", result.output)
        assertEquals(0, result.exitCode)
    }

    @Test
    fun testToolDoesNotExecuteWhenDenied() = runTest {
        val executor = RecordingExecutor(AIAgentCliExecutionResult(output = "should not run", exitCode = 0))
        val tool = AIAgentCliTool(
            profile = AIAgentCliProfiles.codex(),
            executor = executor,
            confirmationHandler = RecordingConfirmationHandler(AIAgentCliConfirmation.Denied("not now"))
        )

        val result = tool.execute(AIAgentCliTool.Args(task = "Change production code"))

        assertEquals(0, executor.calls)
        assertEquals(null, result.exitCode)
        assertEquals("CLI delegation denied with user response: not now", result.output)
    }

    @Test
    fun testToolReturnsFailureResultWhenExecutorThrows() = runTest {
        val executor = ThrowingExecutor(IllegalStateException("missing binary"))
        val profile = AIAgentCliProfiles.claudeCode()
        val tool = AIAgentCliTool(profile, executor, BraveModeAIAgentCliConfirmationHandler())

        val result = tool.execute(AIAgentCliTool.Args(task = "Explain this repository"))

        assertEquals(null, result.exitCode)
        assertEquals("Failed to execute Claude Code CLI: missing binary", result.output)
    }

    @Test
    fun testToolNameUsesSanitizedProfileId() {
        val profile = AIAgentCliProfiles.custom(
            id = "codex/dev.1",
            displayName = "Custom CLI",
            executable = "agent",
            argumentBuilder = AIAgentCliArgumentBuilder { task, _ -> AIAgentCliInvocation(listOf(task)) }
        )
        val tool = AIAgentCliTool(
            profile = profile,
            executor = RecordingExecutor(AIAgentCliExecutionResult(output = "", exitCode = 0)),
            confirmationHandler = BraveModeAIAgentCliConfirmationHandler()
        )

        assertEquals("__delegate_to_codex_dev_1_cli__", tool.name)
    }

    @Test
    fun testToolRejectsBlankTaskAndInvalidTimeout() = runTest {
        val tool = AIAgentCliTool(
            profile = AIAgentCliProfiles.codex(),
            executor = RecordingExecutor(AIAgentCliExecutionResult(output = "", exitCode = 0)),
            confirmationHandler = BraveModeAIAgentCliConfirmationHandler()
        )

        assertFailsWith<IllegalArgumentException> {
            tool.execute(AIAgentCliTool.Args(task = " "))
        }

        assertFailsWith<IllegalArgumentException> {
            tool.execute(AIAgentCliTool.Args(task = "Inspect", timeoutSeconds = -1))
        }
    }

    private val AIAgentCliTool.Args.taskWithContext: String
        get() = buildString {
            append(task.trim())
            val context = context?.trim()
            if (!context.isNullOrEmpty()) {
                appendLine()
                appendLine()
                appendLine("Additional context:")
                append(context)
            }
        }

    private class RecordingExecutor(
        private val result: AIAgentCliExecutionResult,
    ) : AIAgentCliExecutor {
        var calls: Int = 0
        lateinit var lastRequest: AIAgentCliExecutionRequest

        override suspend fun execute(request: AIAgentCliExecutionRequest): AIAgentCliExecutionResult {
            calls++
            lastRequest = request
            return result
        }
    }

    private class ThrowingExecutor(
        private val exception: Exception,
    ) : AIAgentCliExecutor {
        override suspend fun execute(request: AIAgentCliExecutionRequest): AIAgentCliExecutionResult {
            throw exception
        }
    }

    private class RecordingConfirmationHandler(
        private val confirmation: AIAgentCliConfirmation,
    ) : AIAgentCliConfirmationHandler {
        lateinit var lastRequest: AIAgentCliExecutionRequest
        lateinit var lastArgs: AIAgentCliTool.Args

        override suspend fun requestConfirmation(
            request: AIAgentCliExecutionRequest,
            args: AIAgentCliTool.Args,
        ): AIAgentCliConfirmation {
            lastRequest = request
            lastArgs = args
            return confirmation
        }
    }
}
