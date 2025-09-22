package com.example.spring_boot_kotlin_yaml.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import com.example.spring_boot_kotlin_yaml.config.KoogConfiguration
import com.example.spring_boot_kotlin_yaml.model.Models
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class KoogAgentService(
    val googleExecutor: SingleLLMPromptExecutor,
    val koogConfiguration: KoogConfiguration
) {

    suspend fun createAndRunAgent(userPrompt: String): String {
        val agentConfig = AIAgentConfig(
            prompt = prompt("Generic Prompt") {
                system(koogConfiguration.systemPrompt!!.trimIndent())
            },
            model = Models.getLLModel(koogConfiguration.model.id),
            maxAgentIterations = 20
        )

        val executor = googleExecutor

        val agent = AIAgent(
            promptExecutor = executor,
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = provideToolRegistry(koogConfiguration.tools)
        )

        val result = agent.run(userPrompt)

        logger.info { "Agent finished with result: $result" }

        return result
    }

    private suspend fun provideToolRegistry(
        tools: List<KoogConfiguration.ToolDefinition>,
    ): ToolRegistry {
        val toolRegistries = mutableListOf<ToolRegistry>()
        for (toolDefinition in tools) {
            when (toolDefinition.type) {
                KoogConfiguration.ToolType.SIMPLE -> {
//                    Create your own tool according to the doc https://docs.koog.ai/class-based-tools/
//                    toolRegistries.add(ToolRegistry {
//                        tool(MyOwnTool())
//                    })
                }
                KoogConfiguration.ToolType.MCP -> {
                    toolRegistries.add(provideMcpToolRegistry(toolDefinition))
                }
            }
        }

        return if (toolRegistries.isEmpty()) {
            ToolRegistry.EMPTY
        } else if (toolRegistries.size == 1) {
            toolRegistries.first()
        } else {
            toolRegistries.fold(toolRegistries.first()) { acc, registry -> acc + registry }
        }
    }

    private suspend fun provideMcpToolRegistry(toolDefinition: KoogConfiguration.ToolDefinition): ToolRegistry {
        if (toolDefinition.type != KoogConfiguration.ToolType.MCP) throw IllegalArgumentException("MCP tool options not found")

        if (toolDefinition.options.serverUrl != null) {
            val transport = McpToolRegistryProvider.defaultSseTransport(toolDefinition.options.serverUrl)
            return McpToolRegistryProvider.fromTransport(
                transport = transport
            )
        }

        val dockerImage = toolDefinition.options.dockerImage ?: throw IllegalArgumentException("Docker image not found")
        val dockerOptions =
            toolDefinition.options.dockerOptions ?: throw IllegalArgumentException("Docker options not found")
        val dockerEnvVars = dockerOptions.entries.map { (key, value) -> "$key=$value" }

        val dockerCommandList = mutableListOf("docker", "run", "-i", "--rm")
        for (envVar in dockerEnvVars) {
            dockerCommandList.add("-e")
            dockerCommandList.add(envVar)
        }
        dockerCommandList.add(dockerImage)

        // Build the process with the provided Docker image and environment variables
        val processBuilder = ProcessBuilder(dockerCommandList)

        // Start the process
        val process = processBuilder.start()

        // Create and return the MCP tool registry
        return McpToolRegistryProvider.fromTransport(
            transport = McpToolRegistryProvider.defaultStdioTransport(process)
        )
    }
}