package ai.koog.agents.example.codeagent.step05

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.HistoryCompressionConfig
import ai.koog.agents.ext.agent.singleRunStrategyWithHistoryCompression

import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.shell.ExecuteShellCommandTool
import ai.koog.agents.ext.tool.shell.JvmShellCommandExecutor
import ai.koog.agents.ext.tool.shell.PrintShellCommandConfirmationHandler
import ai.koog.agents.ext.tool.shell.ShellCommandConfirmation
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.rag.base.files.JVMFileSystemProvider

val multiExecutor = MultiLLMPromptExecutor(
    LLMProvider.Anthropic to AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY")),
    LLMProvider.OpenAI to OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
)

val agent = AIAgent(
    promptExecutor = multiExecutor,
    llmModel = AnthropicModels.Sonnet_4_5,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
        tool(createExecuteShellCommandToolFromEnv())
        tool(createFindAgentTool())
    },
    systemPrompt = """
        You are a highly skilled programmer tasked with updating the provided codebase according to the given task.
        Your goal is to deliver production-ready code changes that integrate seamlessly with the existing codebase and solve given task.
        Ensure minimal possible changes done - that guarantees minimal impact on existing functionality.
        
        You have shell access to execute commands and run tests.
        After investigation, define expected behavior with test scripts, then iterate on your implementation until the tests pass.
        Verify your changes don't break existing functionality through regression testing, but prefer running targeted tests over full test suites.
        Note: the codebase may be fully configured or freshly cloned with no dependencies installed - handle any necessary setup steps.
        
        You also have an intelligent find micro agent at your disposition, which can help you find code components and other constructs 
        more cheaply than you can do it yourself. Lean on it for any and all search operations. Do not use shell execution for find tasks.
    """.trimIndent(),
    strategy = singleRunStrategyWithHistoryCompression(
        config = HistoryCompressionConfig(
            isHistoryTooBig = CODE_AGENT_HISTORY_TOO_BIG,
            compressionStrategy = CODE_AGENT_COMPRESSION_STRATEGY,
            retrievalModel = OpenAIModels.Chat.GPT4_1Mini
        )
    ),
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

suspend fun main(args: Array<String>) {
    if (args.size < 2) {
        println("Error: Please provide the project absolute path and a task as arguments")
        println("Usage: <absolute_path> <task>")
        return
    }

    val (path, task) = args
    val input = "Project absolute path: $path\n\n## Task\n$task"
    try {
        val result = agent.run(input)
        println(result)
    } finally {
        multiExecutor.close()
    }
}
