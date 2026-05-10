package ai.koog.agents.ext.tool.cli

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Delegates a bounded task to an external AI agent CLI such as Codex CLI, Claude Code CLI, or GitHub Copilot CLI.
 *
 * The tool intentionally accepts task-level inputs only. Command flags and process shape are owned by
 * [AIAgentCliProfile] so host applications can review and lock down each supported CLI integration.
 *
 * @property profile CLI profile used for process construction.
 * @property executor Platform-specific process executor.
 * @property confirmationHandler Approval strategy applied before the process starts.
 */
public class AIAgentCliTool(
    private val profile: AIAgentCliProfile,
    private val executor: AIAgentCliExecutor,
    private val confirmationHandler: AIAgentCliConfirmationHandler,
) : Tool<AIAgentCliTool.Args, AIAgentCliTool.Result>(
    argsType = typeToken<Args>(),
    resultType = typeToken<Result>(),
    name = "__delegate_to_${profile.id.toToolNameSegment()}_cli__",
    description = """
        Delegates a well-scoped software engineering task to ${profile.displayName}.
        Use this when another installed AI coding CLI is better suited to inspect or modify the local workspace.
        Provide a concrete task, optional context, optional absolute working directory, and a timeout.
    """.trimIndent()
) {
    /**
     * Arguments for delegating a task to an external AI agent CLI.
     *
     * @property task Concrete task for the delegated CLI agent.
     * @property context Optional supporting context appended to the delegated prompt.
     * @property workingDirectory Optional absolute filesystem path where the CLI should run.
     * @property timeoutSeconds Optional timeout override for this task.
     */
    @Serializable
    public data class Args(
        @property:LLMDescription(
            "Concrete task to delegate to the external AI coding CLI. Include expected files, behavior, and verification."
        )
        val task: String,
        @property:LLMDescription(
            "Optional supporting context that should be appended to the delegated task."
        )
        val context: String? = null,
        @property:LLMDescription(
            "Optional absolute filesystem path where the CLI should run. If omitted, the profile or process default is used."
        )
        val workingDirectory: String? = null,
        @property:LLMDescription(
            "Optional timeout in seconds. Must be positive when provided."
        )
        val timeoutSeconds: Int? = null,
    )

    /**
     * Result of a delegated CLI task.
     *
     * @property profileId CLI profile identifier.
     * @property command Executed command as an argument vector.
     * @property exitCode Process exit code, or null if the process did not complete normally.
     * @property timedOut True when the process was terminated after reaching its timeout.
     * @property output Output extracted from the CLI process result.
     */
    @Serializable
    public data class Result(
        val profileId: String,
        val command: List<String>,
        val exitCode: Int?,
        val timedOut: Boolean,
        val output: String,
    )

    override suspend fun execute(args: Args): Result {
        val task = renderTask(args)
        val request = profile.buildRequest(task, args)

        return when (val confirmation = confirmationHandler.requestConfirmation(request, args)) {
            is AIAgentCliConfirmation.Approved -> executeApproved(request)
            is AIAgentCliConfirmation.Denied -> Result(
                profileId = profile.id,
                command = request.command,
                exitCode = null,
                timedOut = false,
                output = "CLI delegation denied with user response: ${confirmation.userResponse}"
            )
        }
    }

    private suspend fun executeApproved(request: AIAgentCliExecutionRequest): Result {
        return try {
            val processResult = executor.execute(request)
            Result(
                profileId = profile.id,
                command = request.command,
                exitCode = processResult.exitCode,
                timedOut = processResult.timedOut,
                output = profile.outputExtractor.extract(processResult)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(
                profileId = profile.id,
                command = request.command,
                exitCode = null,
                timedOut = false,
                output = "Failed to execute ${profile.displayName}: ${e.message}"
            )
        }
    }

    override fun encodeResultToString(result: Result, serializer: JSONSerializer): String = with(result) {
        buildString {
            appendLine("CLI profile: $profileId")
            appendLine("Command: ${command.joinToString(" ")}")
            if (output.isNotBlank()) {
                appendLine(output.trimEnd())
            } else if (exitCode != null) {
                appendLine("(no output)")
            }
            if (timedOut) {
                appendLine("Timed out")
            }
            exitCode?.let { appendLine("Exit code: $it") }
        }.trimEnd()
    }

    private fun renderTask(args: Args): String {
        require(args.task.isNotBlank()) { "task must not be blank" }
        args.timeoutSeconds?.let { require(it > 0) { "timeoutSeconds must be positive" } }

        return buildString {
            append(args.task.trim())
            val context = args.context?.trim()
            if (!context.isNullOrEmpty()) {
                appendLine()
                appendLine()
                appendLine("Additional context:")
                append(context)
            }
        }
    }

    private companion object {
        fun String.toToolNameSegment(): String {
            val sanitized = map { char ->
                when (char) {
                    in 'A'..'Z', in 'a'..'z', in '0'..'9', '_' -> char
                    else -> '_'
                }
            }.joinToString("")

            return sanitized.trim('_').ifBlank { "agent" }
        }
    }
}
