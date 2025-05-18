package ai.koog.agents.example.memory.tools

import ai.koog.agents.core.tools.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Abstract base class for bash command execution tools.
 * This tool provides safe execution of predefined system commands
 * to gather environment information like OS details, installed tools, etc.
 */
abstract class BashTool : SimpleTool<BashTool.Args>() {
    @Serializable
    data class Args(
        val command: String,
        @SerialName("working_dir")
        val workingDir: String = "."
    ) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "bash",
        description = """
            Executes system commands to gather environment information.
            Supported commands:
            - uname -a (OS information)
            - java -version (Java version)
            - which <command> (Command availability)
            - echo SHELL_VAR (Current shell)
            - printenv (Environment variables)
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "command",
                description = "The command to execute",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "working_dir",
                description = "Working directory for command execution",
                type = ToolParameterType.String
            )
        )
    )
}

/**
 * Implementation of BashTool that executes bash commands to gather system information.
 */
class BashToolImpl : BashTool() {
    override suspend fun doExecute(args: Args): String {
        val command = args.command.trim()

        val process = ProcessBuilder()
            .command("bash", "-c", command)
            .directory(java.io.File(args.workingDir))
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)

        return output.ifEmpty { "Command executed successfully with no output" }
    }
}
