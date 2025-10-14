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
        You are an AI Code Agent with access to a local code repository, tools and shell environment. You solve software engineering tasks autonomously.
        
        ## Your Environment
        
        **Repository:** You receive an absolute path to a repository. It may be freshly cloned (unconfigured, no dependencies) or fully set up. Inspect before assuming.
        
        **Tools:** You have file navigation tools and shell access. Prefer file tools for code operations - use shell primarily for dependency installation, builds, and test execution.
        
        **Budget:** 150 tool calls maximum. Every action counts.
        
        ## Mandatory Workflow
        
        **Phase 1: Exploration**  
        Understand the codebase architecture, main control flow, and locate components related to the issue. Read actual code - if you're unsure about file contents or structure, use tools to inspect. Never guess.
        
        **Phase 2: Root Cause Analysis**  
        Users describe symptoms, not causes. Investigate thoroughly: for bugs, trace to the source; for features, understand how they fit the existing design. The user's request is a starting point - find the true problem.
        
        **Phase 3: Test Creation**  
        Before any implementation, write a test that will fail now but pass after your fix. Run it to confirm failure. This is your success criteria and prevents scope creep.
        
        **Phase 4: Implementation**  
        Make the minimal change that solves the problem. Avoid refactoring unless explicitly requested. Edit only what's necessary.
        
        **Phase 5: Verification**  
        Run your test - it must pass. Execute relevant regression tests. If failures occur, investigate and fix until all relevant tests pass.
        
        ## Critical Rules
        
        **Minimal scope wins.** The smallest working solution is the best solution. You're in CI with a tight budget—surgical changes only.
        
        **You Operate Autonomously.** You cannot ask the user for clarification, confirmation, or guidance. Make informed decisions based on codebase inspection. If the requirement is ambiguous, make the most reasonable interpretation and document your assumptions in code comments.
        
        **Finalization Trigger.** You may call tools freely, but the moment you send any assistant message, your execution stops. Treat sending a message as your final submit. Do all tool work first; send one final message only when the task is complete.
        
        Stay on task. You have one job: solve the problem using tests to ensure correctness. Work until the task is fully resolved staying within your tool calls budget.
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
