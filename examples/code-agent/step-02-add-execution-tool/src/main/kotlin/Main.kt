package ai.koog.agents.examples.codeagent.step02

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.ext.tool.shell.ExecuteShellCommandTool
import ai.koog.agents.ext.tool.shell.JvmShellCommandExecutor
import ai.koog.agents.ext.tool.shell.PrintShellCommandConfirmationHandler
import ai.koog.agents.ext.tool.shell.ShellCommandConfirmation
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = singleRunStrategy(),
    systemPrompt = """
        You are a surgical software-engineering agent. Do not emit any text until the task is finished or blocked by constraints; text means “done”.
        
        **Mission**
        Complete the user’s task with minimal, correct changes, verified by execution.
        
        **Path & FS rules**
        If a project root is provided, build absolute paths from it. If not, detect the repo root (look for .git, pyproject.toml, package.json) and use paths relative to that. Modify files in place; avoid duplicate variants. Temporary scripts are allowed but must be deleted before finishing.
        
        **Budget & efficiency**
        ≤150 tool calls and ≤30 minutes. Batch shell operations (chain with &&, ;, pipes). Use find/grep -nR to locate targets before reading/editing individual files. Prefer targeted reads/edits.
        
        **Workflow**
            1.	Understand task and define concrete success criteria.
            2.	Explore repository layout (top-level tree; key configs).
            3.	Reproduce the problem or requirement with a single repro.py or command.
            4.	Diagnose by reading relevant code and, if needed, adding/observing prints.
            5.	Implement a minimal patch with clean code and proper imports.
            6.	Verify: re-run reproduction; run existing tests/linters if present; check edge cases surfaced during diagnosis.
            7.	Cleanup: remove temp files and debug output.
        
        **Finish mode**
        If approaching limits (~140 calls or ~25 min), implement the most probable minimal fix, verify once, and finalize.
        
        **Final report (single text output, up to 75 words):**
        STATUS: success | partial
        WHAT I CHANGED: <files + brief diff summary>
        HOW I VERIFIED: <commands/scripts run, results>
        
        **Safety:** avoid destructive commands; never expose secrets or push to remotes.
        """.trimIndent(),
    llmModel = OpenAIModels.Chat.GPT5,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
        tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
        tool(createExecuteShellCommandToolFromEnv())
    },
    maxIterations = 300
) {
    handleEvents {
        onToolCallStarting { ctx ->
            println("Tool '${ctx.tool.name}' called with args: ${ctx.toolArgs.toString().take(100)}")
        }
    }
}

fun createExecuteShellCommandToolFromEnv(): ExecuteShellCommandTool {
    return if (System.getenv("BRAVE_MODE")?.lowercase() == "true") {
        ExecuteShellCommandTool(JvmShellCommandExecutor()) { _ -> ShellCommandConfirmation.Approved }
    } else {
        ExecuteShellCommandTool(JvmShellCommandExecutor(), PrintShellCommandConfirmationHandler())
    }
}

fun main(args: Array<String>) = runBlocking {
    if (args.size < 2) {
        println("Error: Please provide the project absolute path and a task as arguments")
        println("Usage: <absolute_path> <task>")
        return@runBlocking
    }

    val (path, task) = args
    val input = "Project path: $path\n\n$task"
    val result = agent.run(input)
    println(result)
}
