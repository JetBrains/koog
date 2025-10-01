package ai.koog.agents.examples.codeagent.step01

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.integration.langfuse.addLangfuseExporter
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
    """.trimIndent(),
    llmModel = OpenAIModels.Chat.GPT5,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
        tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
    },
    maxIterations = 100
) {
    install(OpenTelemetry) {
        setVerbose(true) // Enable verbose mode to send full strings instead of HIDDEN placeholders
        addLangfuseExporter(
            langfuseUrl = "https://cloud.langfuse.com",
            langfusePublicKey = System.getenv("LANGFUSE_PUBLIC_KEY"),
            langfuseSecretKey = System.getenv("LANGFUSE_SECRET_KEY"),
            traceAttributes = listOf(
                CustomAttribute("langfuse.session.id", System.getenv("LANGFUSE_SESSION_ID") ?: ""),
            )
        )
    }
    handleEvents {
        onToolExecutionStarting { ctx ->
            println("Tool called: ${ctx.tool.name}")
        }
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
