package ai.grazie.code.agents.example.subgraphwithtask


import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.TokenService
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.CreateDirectoryTool
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.CreateFileTool
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.DeleteDirectoryTool
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.DeleteFileTool
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.LSDirectoriesTool
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.ReadFileTool
import ai.grazie.code.agents.example.subgraphwithtask.ProjectGeneratorTools.RunCommand
import ai.grazie.code.agents.core.KotlinAIAgent
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.coroutines.CoroutineScope
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
        stage("generate-verify-and-fix") {
            verifyTools.forEach { tool(it) }
            fixTools.forEach { tool(it) }
        }
    }

    runBlocking {
        /**
         * Read user request from standard input.
         */
        println()
        println("I am agent that can generate a project structure for you. Enter your project description and some details (if possible) like language, framework, etc.: ")
        println("       (possible example: Generate an online book store in Java/Gradle with Spring Framework and PostgreSQL database. Language: Java, Framework: Spring, Database)")
        val userRequest = readln()

        val agent = KotlinAIAgent(
            toolRegistry = toolRegistry,
            strategy = customWizardStrategy(generateTools, verifyTools, fixTools),
            agentConfig = LocalAgentConfig(
                prompt = prompt(
                    AnthropicModels.Sonnet_3_7, "chat",
                    params = LLMParams()
                ) {},
                maxAgentIterations = 200
            ),
            promptExecutor = simpleAnthropicExecutor(TokenService.anthropicToken),
            cs = CoroutineScope(coroutineContext),
        )

        agent.run(userRequest)
    }
}