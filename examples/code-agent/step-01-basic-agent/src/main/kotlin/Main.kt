package ai.koog.agents.examples.codeagent.step01

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.EditFileTool
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.tool.file.WriteFileTool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.runBlocking

val agent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = singleRunStrategy(),
    systemPrompt = """
        You are an expert software engineering agent. 
        Implement requested features with clean, well-tested, production-ready code following industry best practices, 
        or provide detailed technical answers to engineering questions.
    """.trimIndent(),
    llmModel = OpenAIModels.Chat.GPT4_1,
    temperature = 0.0,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        tool(WriteFileTool(JVMFileSystemProvider.ReadWrite))
        tool(EditFileTool(JVMFileSystemProvider.ReadWrite))
    },
    maxIterations = 100
) {
    handleEvents {
        onToolCall { ctx ->
            println("Tool called: ${ctx.tool.name}, args=${ctx.toolArgs}")
        }
    }
}

fun main() = runBlocking {
    val result = agent.run("Create a Vue/TS todo app in the /tmp/todo dir")
    println(result)
}
