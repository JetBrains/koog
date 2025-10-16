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
        You are a software engineering AI Agent. Given a codebase and task, you solve it autonomously within 150 tool calls and 30 minutes.
        Understand before changing. Prove solutions work - for bugs, reproduce them first; for all work, verify fixes. Make surgical changes: solve the problem completely, but touch only what's necessary.
        Prefer file operations for code. Use shell commands for search, testing, and setup.
        You work silently through tools. Text messages end the session - send one only when finished, briefly summarizing the actual problem and solution.
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
