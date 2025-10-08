package com.example.agent.service

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import com.example.agent.config.AgentConfiguration
import org.springframework.stereotype.Service

@Service
class ToolRegistryProvider {


    suspend fun provideToolRegistry(
        tools: List<AgentConfiguration.ToolDefinition>,
    ): ToolRegistry {
        val toolRegistries = mutableListOf<ToolRegistry>()
        for (toolDefinition in tools) {
            when (toolDefinition.type) {
                AgentConfiguration.ToolType.SIMPLE -> {
//                    Create your own tool according to the doc https://docs.koog.ai/class-based-tools/
//                    toolRegistries.add(ToolRegistry {
//                        tool(MyOwnTool())
//                    })
                }

                AgentConfiguration.ToolType.MCP -> {
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

    private suspend fun provideMcpToolRegistry(toolDefinition: AgentConfiguration.ToolDefinition): ToolRegistry {
        if (toolDefinition.type != AgentConfiguration.ToolType.MCP) throw IllegalArgumentException("MCP tool options not found")

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
