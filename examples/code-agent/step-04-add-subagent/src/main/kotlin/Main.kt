package ai.koog.agents.examples.codeagent.step04

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.createAgentTool
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.shell.ExecuteShellCommandTool
import ai.koog.agents.ext.tool.shell.JvmShellCommandExecutor
import ai.koog.agents.ext.tool.shell.PrintShellCommandConfirmationHandler
import ai.koog.agents.ext.tool.shell.ShellCommandConfirmation
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = singleRunStrategy(),
    systemPrompt = """
        You are a highly skilled programmer tasked with updating the provided codebase according to the given task.
        Your goal is to deliver production-ready code changes that integrate seamlessly with the existing codebase and solve given task.
        Ensure minimal possible changes done - that guarantees minimal impact on existing functionality.
        
        You have shell access to execute commands and run tests.
        After investigation, define expected behavior with test scripts, then iterate on your implementation until the tests pass.
        Verify your changes don't break existing functionality through regression testing, but prefer running targeted tests over full test suites.
        Note: the codebase may be fully configured or freshly cloned with no dependencies installed - handle any necessary setup steps.
    """.trimIndent(),
    llmModel = OpenAIModels.Chat.GPT5,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
        tool(createExecuteShellCommandToolFromEnv())
        tool(createFindAgentTool())
    },
    maxIterations = 400
) {
    setupObservability(agentName = "main")
}

fun createExecuteShellCommandToolFromEnv(): ExecuteShellCommandTool {
    return if (System.getenv("BRAVE_MODE")?.lowercase() == "true") {
        ExecuteShellCommandTool(JvmShellCommandExecutor()) { _ -> ShellCommandConfirmation.Approved }
    } else {
        ExecuteShellCommandTool(JvmShellCommandExecutor(), PrintShellCommandConfirmationHandler())
    }
}

fun createFindAgentTool(): Tool<*, *> {
    return AIAgentService
        .fromAgent(findAgent as GraphAIAgent<String, String>)
        .createAgentTool<String, String>(
            agentName = "__find_in_codebase__",
            agentDescription = """
                This tool is powered by an intelligent micro agent that analyzes and understands code context to find specific elements in your codebase.
                Unlike simple text search (ctrl+F), it intelligently interprets your query to locate classes, functions, variables, or files that best match your intent.
                It requires a detailed query describing what to search for, why you need this information, and an absolute path defining the search scope.
                
                When to use:
                - Locating specific declarations or implementations with contextual understanding.
                - Finding relevant usages of code elements across the codebase.
                - Discovering files and code patterns related to your specific needs.
                - When you need intelligent search that understands code structure and semantics.
                
                When NOT to use:
                - Broad, ambiguous, or conceptual searches (e.g., 'find code related to payments' without specific identifiers).
                - Code understanding, explanation, or refactoring suggestions.
                - Searching outside the provided `path` directory.
                
                The agent analyzes your query, searches intelligently, and returns findings with file paths, line numbers, and relevant code snippets, along with explanations of why each result matches your needs.
            """.trimIndent(),
            inputDescription = """
                The input contains two components: the absolute_path and the query.
                
                ## Query
                The query is a detailed search query for the intelligent agent to analyze. Unlike simple text search, this agent will understand your intent if you explain it clearly enough.
                The more details you provide, the better the agent can understand your needs and deliver relevant results. 
                Focus on identifiable code structures (class/function names, variable names, specific text snippets, file name patterns). 
                
                Examples of effective queries:
                - Find all implementations of the `UserRepository` interface to understand how data persistence is handled across the application
                - Locate files named `*Service.kt` containing `fun processOrder` because I need to modify the order processing logic to add a new discount feature
                - Find usages of the `calculateDiscount` function as I'm investigating a bug where discounts are incorrectly applied
                - Search for the text 'OAuth authentication failed' to understand how the application handles authentication failures
                - Find class `PaymentProcessor` because I need to add support for a new payment method
                
                Avoid vague queries like 'search for payment logic'
                Always structure your query as: what you're looking for + why you need it.
                
                ## absolute_path
                The absolute file system path to the directory where the search should begin (the search scope).
                This is crucial for focusing the search on the relevant part of the codebase (e.g., the project root, a specific module, or source directory).
                The path must be absolute and correctly formatted for the operating system.
                
                Example: `/my_app/src/main/kotlin`
                
                ## Formatting
                Provide the absolute_path and the query in this format: 'Absolute path for search scope: <absolute_path>\\n\\n## Query\\n<query>'."
            """.trimIndent()
        )
}

fun main(args: Array<String>) = runBlocking {
    if (args.size < 2) {
        println("Error: Please provide the project absolute path and a task as arguments")
        println("Usage: <absolute_path> <task>")
        return@runBlocking
    }

    val (path, task) = args
    val input = "Project absolute path: $path\n\n## Task\n$task"
    val result = agent.run(input)
    println(result)
}
