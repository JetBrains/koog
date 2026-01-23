package ai.koog.protocol.cli

import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.flow.toKoogFlow
import ai.koog.protocol.parser.FlowJsonConfigParser
import java.io.File
import java.io.PrintStream

private val HELP_TEXT = """
Usage: flow <file.json> [options]

Options:
  -i, --input <text>   Input text to pass to the flow (auto-derived from JSON if omitted)
  -v, --verbose        Print progress information to stderr
  -h, --help           Show this help message and exit

Exit codes:
  0  Success
  1  Flow execution error
  2  Bad arguments or missing file
  3  JSON parse error

Examples:
  flow basic_task_flow.json
  flow basic_task_flow.json --input "Solve this problem"
  flow basic_task_flow.json -i "Hello" --verbose
""".trimIndent()

internal class FlowCli(
    private val stdout: PrintStream = System.out,
    private val stderr: PrintStream = System.err,
) {

    suspend fun run(args: Array<String>) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            stdout.println(HELP_TEXT)
            throw ExitException(if (args.isEmpty()) 2 else 0)
        }

        var filePath: String? = null
        var inputText: String? = null
        var verbose = false

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "--input", "-i" -> {
                    i++
                    if (i >= args.size) {
                        stderr.println("Error: --input requires a value")
                        throw ExitException(2)
                    }
                    inputText = args[i]
                }
                "--verbose", "-v" -> verbose = true
                else -> {
                    if (arg.startsWith("-")) {
                        stderr.println("Error: Unknown option: $arg")
                        throw ExitException(2)
                    }
                    if (filePath != null) {
                        stderr.println("Error: Multiple file paths provided")
                        throw ExitException(2)
                    }
                    filePath = arg
                }
            }
            i++
        }

        if (filePath == null) {
            stderr.println("Error: No JSON file specified")
            stdout.println(HELP_TEXT)
            throw ExitException(2)
        }

        val file = File(filePath)
        if (!file.exists()) {
            stderr.println("Error: File not found: $filePath")
            throw ExitException(2)
        }

        val content = file.readText()

        val config = try {
            FlowJsonConfigParser().parse(content)
        } catch (e: Exception) {
            stderr.println("Error: Failed to parse JSON: ${e.message}")
            throw ExitException(3)
        }

        if (verbose) {
            val agentNames = config.agents.map { it.name }
            stderr.println("[flow] Agents: ${agentNames.joinToString(" → ")}")
        }

        val flow = config.toKoogFlow()
        val input = inputText?.let { FlowDataType.FlowString(it) }

        val result = try {
            if (verbose) stderr.println("[flow] Running flow: ${config.id ?: flow.id}")
            flow.run(input)
        } catch (e: Throwable) {
            stderr.println("Error: Flow execution failed: ${e.message}")
            throw ExitException(1)
        }

        if (verbose) stderr.println("[flow] Flow complete.")

        stdout.println(OutputFormatter.format(result))
    }
}
