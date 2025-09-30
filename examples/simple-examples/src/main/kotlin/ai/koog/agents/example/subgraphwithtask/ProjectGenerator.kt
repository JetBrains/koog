package ai.koog.agents.example.subgraphwithtask

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.CreateDirectoryTool
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.CreateFileTool
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.DeleteDirectoryTool
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.DeleteFileTool
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.LSDirectoriesTool
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.ReadFileTool
import ai.koog.agents.example.subgraphwithtask.ProjectGeneratorTools.RunCommand
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path

private fun prepareTempDir(): Path {
    val dir = File("./project-gen-test-project")
    dir.mkdirs()

    return dir.toPath()
}

fun main() {
    val rootProjectPath = prepareTempDir()

    val generateTools = listOf(
        CreateFileTool(rootProjectPath),
        CreateDirectoryTool(rootProjectPath)
    )
    val verifyTools = listOf(
        RunCommand(rootProjectPath),
        ReadFileTool(rootProjectPath),
        LSDirectoriesTool(rootProjectPath),
    )

    val fixTools = listOf(
        CreateFileTool(rootProjectPath),
        CreateDirectoryTool(rootProjectPath),
        DeleteDirectoryTool(rootProjectPath),
        DeleteFileTool(rootProjectPath)
    )

    /**
     * Describe the list of tools for your agent.
     */
    val toolRegistry = ToolRegistry {
        verifyTools.forEach { tool(it) }
        fixTools.forEach { tool(it) }
    }

    runBlocking {
        /**
         * Read user request from standard input.
         */
        println()
        println(
            "I am agent that can generate a project structure for you. Enter your project description and some details (if possible) like language, framework, etc.: "
        )
        println(
            "       (possible example: Generate an online book store in Java/Gradle with Spring Framework and PostgreSQL database. Language: Java, Framework: Spring, Database)"
        )
        val userRequest = readln()

        val agent = AIAgent(
            promptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            strategy = customWizardStrategy(generateTools, verifyTools, fixTools),
            agentConfig = AIAgentConfig(
                prompt = prompt(
                    "chat",
                    params = LLMParams()
                ) {},
                model = OpenAIModels.Chat.GPT4o,
                maxAgentIterations = 200
            ),
            toolRegistry = toolRegistry,
        )

        agent.run(userRequest)
    }
}
