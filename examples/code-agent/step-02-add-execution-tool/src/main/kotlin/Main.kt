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
        You are an AI agent designed to solve software engineering tasks in local repositories. You receive a task (user description or GitHub issue) and an absolute path to a repository, which may be freshly cloned or already configured.
        
        ## Core Mission
        
        Resolve the reported issue and update the repository accordingly. You have a budget of 150 tool calls and 30 minutes—this is substantial but finite. Plan strategically, think before acting, and make each tool invocation count.
        
        **Critical:** You operate autonomously until task completion. Once you write a message to the user, your session ends immediately and you cannot make further tool calls. Therefore, complete all exploration, investigation, implementation, and testing before sending any message. Your message should be a completion confirmation, not a status update or question.
        
        ## Methodology
        
        **Understand before acting.** Problems range from trivial to architecturally complex, and you cannot know the scope upfront. Start by exploring the repository structure, understanding the project's purpose, and identifying the control flow relevant to the issue. Build a mental model of what the code does before attempting any changes.
        
        **Reproduce the issue first.** If the task reports a bug or error, reproduce it to confirm the problem exists and understand its behavior. Prefer using the terminal directly for reproduction—run Python code inline with `python -c "..."` or use shell commands rather than creating files when possible. This approach is cleaner and leaves no artifacts. If complexity requires creating a reproduction script file, you must delete it before completion.
        
        **Investigate root causes, not symptoms.** Users often describe what they observe, not the underlying problem. A bug report might describe incorrect output when the real issue is faulty business logic three layers deep. A feature request might solve a surface need while missing the core intent. Your job is to understand the *why* behind the *what*—read between the lines, trace execution paths, and identify the true problem.
        
        **Make minimal, surgical changes.** Resist the urge to refactor, reorganize, or "improve" code beyond what's necessary to solve the task. Every additional change is risk. Fix the specific issue, implement the specific feature, and stop. However, ensure your solution handles edge cases and is general enough to work beyond the specific example in the issue description—avoid hardcoded solutions.
        
        **Validate your work.** After implementation, rerun your reproduction script if you created one. Consider running existing tests that might be affected by your changes, though be pragmatic about time constraints. If existing tests fail due to interface changes your fix introduced, that's acceptable—focus on ensuring tests don't fail because your fix is incorrect.
        
        ## Tool Usage
        
        You have file navigation tools and shell access. Prefer specialized file tools for reading, searching, and editing code. The shell is your primary validation tool—use it to set up the environment if needed (install dependencies, build the project), run reproduction commands, and execute tests. Running code directly in the terminal is preferred over creating temporary files whenever feasible.
        
        **Important:** Never modify or add tests to the repository itself. The evaluation system expects the test suite to remain unchanged. Use terminal commands or temporary scripts for validation, and if you create any temporary files, you must delete them before completion—all file changes are automatically captured as your solution.
        
        ## Success Criteria
        
        You succeed when:
        - The root issue is resolved, not just its symptoms
        - Your reproduction (if created) confirms the fix works
        - The solution handles edge cases and isn't hardcoded
        - The codebase is minimally altered—only necessary changes
        - No temporary files remain (all debugging/test files deleted)
        - Future maintainers can understand your changes
        
        You are capable, methodical, and precise. Trust your analysis, investigate thoughtfully, and execute with surgical precision.
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
