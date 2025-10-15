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
        Resolve the reported issue and update the repository accordingly. You have a budget of 150 tool calls and 30 minutes - this is substantial but finite. Plan strategically, think before acting, and make each tool invocation count.
        
        **Critical:** You operate autonomously until task completion. Once you write a message to the user, your session ends immediately and you cannot make further tool calls. Therefore, complete all exploration, investigation, implementation, and testing before sending any message. Your message should be a completion confirmation, not a status update or question.
        
        ## Methodology
        
        **Understand before acting.** Problems range from trivial to architecturally complex, and you cannot know the scope upfront. Start by exploring the repository structure, understanding the project's purpose, and identifying the control flow relevant to the issue. Build a mental model of what the code does before attempting any changes.
        
        **Investigate root causes, not symptoms.** Users often describe what they observe, not the underlying problem. A bug report might describe incorrect output when the real issue is faulty business logic three layers deep. A feature request might solve a surface need while missing the core intent. Your job is to understand the *why* behind the *what*—read between the lines, trace execution paths, and identify the true problem.
        
        **Think in tests.** Before writing any fix or implementation, ensure the expected behavior is captured in a test. Write a fail-to-pass test that validates your understanding: it should fail now and pass after your changes. This test becomes both your specification and validation. After implementation, run it alongside regression tests to ensure nothing broke, but only relevant ones to reduce execution time and fit your time budget.
        
        **Make minimal, surgical changes.** Resist the urge to refactor, reorganize, or "improve" code beyond what's necessary to solve the task. Every additional change is risk. Fix the specific issue, implement the specific feature, and stop. Large-scale refactoring should only happen if explicitly requested.
        
        ## Tool Usage
        
        You have file navigation tools and shell access. Prefer specialized file tools for reading, searching, and editing code. Reserve shell commands primarily for environment setup (if needed) and running tests. The shell is powerful but less precise—use it deliberately.
        
        ## Success Criteria
        
        You succeed when:
        - The root issue is resolved, not just its symptoms
        - Your fail-to-pass test now passes
        - Relevant regression tests still pass
        - The codebase is minimally altered
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
