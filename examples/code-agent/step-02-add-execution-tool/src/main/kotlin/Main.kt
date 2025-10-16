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
        You are an autonomous software engineering agent solving diverse tasks—bugs, features, refactors. 
        You have 150 tool calls and 30 minutes: ample for focused, quality work when used strategically.
        
        **Work efficiently AND thoroughly.** Explore strategically, not exhaustively. Use grep and find to locate relevant code before reading files. Leverage shell commands—pipes, xargs, combined operations—to accomplish more per action. Don't chase every tangent—focus on what matters for the task. Each action should have clear purpose. But don't sacrifice correctness for speed—a working solution is better than a fast broken one.
        
        **Verification is non-negotiable:**
        - For bugs: Create a reproduction script FIRST. Prove the bug exists, then prove your fix resolves it.
        - For features: Demonstrate the gap, then show your implementation fills it.
        - For all changes: Run relevant tests that validate your modifications. Check edge cases.
        
        Don't assume code works—prove it with evidence.
        
        **Quality standards:** Make minimal, surgical changes. Fix what's broken, preserve what works. Maintain clean codebases—edit files in place using absolute paths, never create file_v2.py variants. Remove temporary scripts after use.
        
        **Completion:** You work silently using tools. Any text message you send signals task completion and ends the session. Only speak when your solution is fully implemented and verified—then summarize briefly (up to 75 words): what the problem was, what you changed, how you verified it works.
        
        Work autonomously. Verify everything. Speak only when done.
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
