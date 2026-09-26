@file:Suppress("DEPRECATION")

package org.example.agents.coding

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.ext.tool.search.RegexSearchTool
import ai.koog.agents.ext.tool.shell.BraveModeConfirmationHandler
import ai.koog.agents.ext.tool.shell.ExecuteShellCommandTool
import ai.koog.agents.ext.tool.shell.JvmShellCommandExecutor
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
@LLMDescription("Summary of MAIN (significant) changes in the code")
data class ChangesSummary(
    @property:LLMDescription("Goal for all the changes (why they have been made)")
    val goal: String,
    @property:LLMDescription("List of significant (only important) changes and their descriptions (reasons + summaries)")
    val mainChanges: List<ChangeDescription>
)

@Serializable
@LLMDescription("Description of an important code change")
data class ChangeDescription(
    @property:LLMDescription("Brief explanation of the change")
    val changeSummary: String,
    @property:LLMDescription("Why the change was made")
    val reason: String
)

@Serializable
@LLMDescription("List of problems and their summaries")
data class ProblemsList(
    @property:LLMDescription("List of problem summaries")
    val problems: List<String> = emptyList(),
    @property:LLMDescription("Fully qualified names of all classes with missing tests")
    val untestedClasses: List<String> = emptyList(),
    @property:LLMDescription("List of all failed tests (<class name>.<test method name>)")
    val failedTests: List<String> = emptyList()
) {
    fun noProblems(): Boolean = problems.isEmpty() && untestedClasses.isEmpty() && failedTests.isEmpty()
}

@OptIn(InternalAgentToolsApi::class)
fun main() {
    val promptExecutor = MultiLLMPromptExecutor(
        OpenAILLMClient(System.getenv("OPENAI_API_KEY")),
        AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY")),
        GoogleLLMClient(System.getenv("GOOGLE_API_KEY")),
        MistralAILLMClient(System.getenv("MISTRAL_API_KEY")),
        OllamaClient() // localhost
    )

    val fs = JVMFileSystemProvider.ReadOnly
    val fsRW = JVMFileSystemProvider.ReadWrite
    val shellExecutor = JvmShellCommandExecutor()

    val readFile = ReadFileTool(fs)
    val writeFile = WriteFileTool(fsRW)
    val listDir = ListDirectoryTool(fs)
    val search = RegexSearchTool(fs)
    val executeCommand = ExecuteShellCommandTool(shellExecutor, BraveModeConfirmationHandler())

    val graphStrategy = createGraphStrategy(readFile, writeFile, listDir, search, executeCommand)

    val codingWorkflow = AIAgent(
        promptExecutor = promptExecutor,
        systemPrompt = """
            You are professional senior software engineer with 10 years of experience.
            You code in Kotlin and Java.
            Make sure to produce high-quality code that is well-documented and follows best practices.
            Also make sure that your code always compiles and runs without errors.
            Make sure it also passes all tests and that everything you produce is well-tested.
        """.trimIndent(),
        toolRegistry = ToolRegistry {
            tool(readFile)
            tool(writeFile)
            tool(listDir)
            tool(search)
            tool(executeCommand)
        },
        strategy = graphStrategy,
        maxIterations = 1000,
        llmModel = AnthropicModels.Sonnet_4_5,
    ) {
        install(EventHandler) {
            onToolCallStarting { ctx ->
                println("Tool ${ctx.toolName} was called with args ${ctx.toolArgs}")
            }
        }

        install(OpenTelemetry) {
//            addSpanExporter(...)
//            addSpanProcessor(...)

            addLangfuseExporter(
                langfuseUrl = "https://langfuse.com",
                langfusePublicKey = System.getenv("LANGFUSE_PUBLIC_KEY"),
                langfuseSecretKey = System.getenv("LANGFUSE_SECRET_KEY"),
            )

            setVerbose(true)
        }
    }

    println("Enter task:")
    val task = readln()

    runBlocking {
        val result = codingWorkflow.run(task)
        println(result)
    }
}
