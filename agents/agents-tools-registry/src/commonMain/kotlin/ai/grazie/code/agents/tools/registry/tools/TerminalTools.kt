package ai.grazie.code.agents.tools.registry.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.Serializable

object TerminalTools {
    abstract class ExecuteCommandTool : Tool<ExecuteCommandTool.Args, ExecuteCommandTool.Result>() {
        @Serializable
        data class Args(
            val command: String,
            val workingDirectory: String,
            val requiresUserConfirmation: Boolean,
            val agentArgumentation: String = "No additional argumentation provided."
        ) : Tool.Args

        @Serializable
        sealed class ExecutionStatus {
            @Serializable
            data object Completed : ExecutionStatus()

            @Serializable
            data class TimedOut(val seconds: Long) : ExecutionStatus()

            @Serializable
            data class WarningOrError(val message: String) : ExecutionStatus()

            @Serializable
            data class UserRejected(val reason: String) : ExecutionStatus()
        }

        @Serializable
        data class Result(
            val status: ExecutionStatus,
            val output: String
        ) : ToolResult {
            override fun toStringDefault(): String {
                return buildString {
                    appendLine()

                    when (val status = status) {
                        is ExecutionStatus.Completed -> {
                            appendLine("<Command Status: Completed>")
                            appendLine("<Output>")
                            appendLine(output)
                            appendLine("</Output>")
                        }

                        is ExecutionStatus.TimedOut -> {
                            appendLine("<Command Status: TimedOut>")
                            appendLine("<Execution Time: ${status.seconds} seconds>")
                            appendLine("<Partial Output>")
                            appendLine(output)
                            appendLine("</Partial Output>")
                        }

                        is ExecutionStatus.WarningOrError -> {
                            appendLine("<Command Status: Warning or error>")
                            appendLine("<Warning/Error>")
                            appendLine(status.message)
                            appendLine("</Warning/Error>")
                            appendLine("<Output>")
                            appendLine(output)
                            appendLine("</Output>")
                        }

                        is ExecutionStatus.UserRejected -> {
                            appendLine("<Command Status: UserRejected>")
                            appendLine("<Rejection Reason>")
                            appendLine(status.reason)
                            appendLine("</Rejection Reason>")
                            appendLine("<Message>")
                            appendLine(output)
                            appendLine("</Message>")
                        }
                    }

                    trimEnd()
                    appendLine()
                }
            }
        }

        final override val argsSerializer = Args.serializer()


        final override val descriptor = ToolDescriptor(
            name = "execute_command",
            description = """
                Executes a command in the terminal.

                The command will be executed in the specified working directory.
                Returns the command output (both stdout and stderr).

                IMPORTANT NOTES ABOUT COMMAND CONFIRMATION:
                1. Agent Confirmation vs Command Confirmation:
                   - Agent Confirmation: Set by 'requires_user_confirmation' parameter - asks user permission before executing
                   - Command Confirmation: Interactive prompts from the command itself (like 'Are you sure? [y/N]')

                2. Command Confirmation Handling:
                   - This tool CANNOT handle interactive command prompts
                   - Always use command flags to prevent interactive prompts (examples):
                     * Use 'rm -f' instead of 'rm'
                     * Use 'apt-get install -y' instead of 'apt-get install'
                     * Use '--no-input' or similar flags when available
                   - If you can't avoid command prompts, choose alternative commands

                BE CAUTIOUS:
                * Avoid commands that run indefinitely:
                    - They will not return control back to you (the Agent)
                    - You won't get the chance to call cmd+C on those
                    - Consider running them as background processes
                    - Make note of when to shut them down later

                USAGE GUIDELINES:
                1. User Confirmation Requirements:
                   - Always use 'requires_user_confirmation=true' for any write operations or installations
                   - You may use 'requires_user_confirmation=false' for read-only operations like:
                     * Listing files
                     * Reading file content
                     * Checking system information
                     * Verifying tool versions
                2. Command Verification:
                   - After each command execution, verify its output to ensure the intended effect was achieved
                """.trimIndent(),
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "command",
                    description = """
                        The command to execute. 
                        IMPORTANT: Include appropriate flags (-y, -f, --no-input, etc.) to prevent interactive prompts from the command itself.
                        """.trimIndent(),
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "workingDirectory",
                    description = "The working directory where the command will be executed. Should be an absolute path in the system.",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "requiresUserConfirmation",
                    description = """
                        Whether the agent should ask for user permission before executing this command.
                        This is different from command's own interactive prompts, which must be prevented using appropriate command flags.
                        """.trimIndent(),
                    type = ToolParameterType.Boolean
                ),
                ToolParameterDescriptor(
                    name = "agentArgumentation",
                    description = "Optional argumentation for the agent to explain why this command should be executed.",
                    type = ToolParameterType.String,
                    defaultValue = "No additional argumentation provided."
                )
            )
        )
    }
}
