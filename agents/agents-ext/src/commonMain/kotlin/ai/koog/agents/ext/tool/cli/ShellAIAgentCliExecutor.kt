package ai.koog.agents.ext.tool.cli

import ai.koog.agents.ext.tool.shell.ShellCommandExecutor

/**
 * CommonMain implementation of [AIAgentCliExecutor] backed by Koog's shell executor abstraction.
 *
 * This class contains the CLI-specific execution implementation in common code: it renders the trusted
 * executable/argument vector from [AIAgentCliExecutionRequest] into a shell command, delegates the platform process
 * boundary to [ShellCommandExecutor], and maps the result back to [AIAgentCliExecutionResult].
 *
 * @property shellCommandExecutor Platform shell executor supplied by the host application.
 * @property commandLineRenderer Renderer that converts an argument vector into a shell command line.
 */
public class ShellAIAgentCliExecutor(
    private val shellCommandExecutor: ShellCommandExecutor,
    private val commandLineRenderer: AIAgentCliShellCommandLineRenderer = PosixAIAgentCliShellCommandLineRenderer,
) : AIAgentCliExecutor {
    override suspend fun execute(request: AIAgentCliExecutionRequest): AIAgentCliExecutionResult {
        val shellResult = shellCommandExecutor.execute(
            command = commandLineRenderer.render(request),
            workingDirectory = request.workingDirectory,
            timeoutSeconds = request.timeoutSeconds
        )

        return AIAgentCliExecutionResult(
            output = shellResult.output,
            exitCode = shellResult.exitCode,
            timedOut = shellResult.timedOut,
        )
    }
}

/**
 * Renders an AI agent CLI request into a command line accepted by [ShellCommandExecutor].
 */
public fun interface AIAgentCliShellCommandLineRenderer {
    /**
     * Converts [request] into a shell command line.
     *
     * @param request Fully built CLI execution request.
     * @return Shell command line to pass to [ShellCommandExecutor].
     */
    public fun render(request: AIAgentCliExecutionRequest): String
}

/**
 * POSIX shell renderer for AI agent CLI requests.
 *
 * Every executable and argument is single-quoted, single quotes inside values are escaped, environment variable names
 * are validated, and optional standard input is provided through `printf`.
 */
public object PosixAIAgentCliShellCommandLineRenderer : AIAgentCliShellCommandLineRenderer {
    override fun render(request: AIAgentCliExecutionRequest): String {
        val command = buildString {
            if (request.environment.isNotEmpty()) {
                append(renderEnvironment(request.environment))
                append(' ')
            }
            append(request.command.joinToString(" ") { it.posixQuote() })
        }

        val stdin = request.stdin
        return if (stdin == null) {
            command
        } else {
            "printf %s ${stdin.posixQuote()} | $command"
        }
    }

    private fun renderEnvironment(environment: Map<String, String>): String {
        return environment.entries.joinToString(" ") { (name, value) ->
            require(name.isValidEnvironmentName()) { "Invalid environment variable name: $name" }
            "$name=${value.posixQuote()}"
        }
    }

    private fun String.isValidEnvironmentName(): Boolean {
        if (isEmpty()) return false
        val first = first()
        if (first != '_' && first !in 'A'..'Z' && first !in 'a'..'z') return false
        return drop(1).all { it == '_' || it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
    }

    private fun String.posixQuote(): String = "'${replace("'", "'\"'\"'")}'"
}
