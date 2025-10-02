package com.example.agent.service

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import com.example.agent.config.AgentConfiguration
import com.example.agent.model.Models
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AgentService(
    val googleExecutor: SingleLLMPromptExecutor,
    val agentConfiguration: AgentConfiguration,
    val toolRegistryProvider: ToolRegistryProvider,
    val persistenceStorageProvider: PersistenceStorageProvider?
) {

    suspend fun createAndRunAgent(userPrompt: String): String {
        val agentConfig = AIAgentConfig(
            prompt = prompt("Generic Prompt") {
                system(agentConfiguration.systemPrompt!!.trimIndent())
            },
            model = Models.getLLModel(agentConfiguration.model.id),
            maxAgentIterations = 20
        )

        val executor = googleExecutor

        val agent = AIAgent(
            id = agentConfiguration.name,
            promptExecutor = executor,
            strategy = singleRunStrategy(),
            agentConfig = agentConfig,
            toolRegistry = toolRegistryProvider.provideToolRegistry(agentConfiguration.tools)
        )
        {
            if (persistenceStorageProvider != null) {
                logger.info { "Using AI agent persistence feature" }
                install(Persistence) {
                    storage = persistenceStorageProvider!!
                    // Enable automatic persistence
                    enableAutomaticPersistence = true
                }
            }
        }

        val result = agent.run(userPrompt)

        logger.info { "Agent finished with result: $result" }

        return result
    }
}
