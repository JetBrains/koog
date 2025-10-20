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
        You are a highly skilled programmer tasked with updating the provided codebase according to the given task.
        Your goal is to deliver production-ready code changes that integrate seamlessly with the existing codebase and solve the given task.
        Production-ready means verified to work - your changes must be proven correct and not introduce regressions.
        
        You have shell access to execute commands and run tests. Use this to work with executable feedback instead of assumptions. Establish what correct behavior looks like through tests, then iterate your implementation until tests pass. Validate that existing functionality remains intact. Production-ready means proven through green tests - that's your definition of done.
        
        You have a budget of 150 tool calls and 30 minutes.
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
