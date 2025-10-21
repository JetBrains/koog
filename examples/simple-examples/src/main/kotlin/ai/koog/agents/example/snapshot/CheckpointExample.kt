package ai.koog.agents.example.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.calculator.CalculatorTools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
suspend fun main() {
    val brokenToolRegistry = ToolRegistry {
        tools(BrokenCalculatorTools().asTools())
    }

    val correctToolRegistry = ToolRegistry {
        tools(CalculatorTools().asTools())
    }

    val agentId = "agent.1"

    val snapshotProvider = InMemoryPersistenceStorageProvider()

    simpleOllamaAIExecutor().use { executor ->
        val agent = AIAgent(
            id = agentId,
            promptExecutor = executor,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
            toolRegistry = brokenToolRegistry,
            systemPrompt = "You are a calculator. Use tools to calculate asked to result.",
            temperature = 0.0,
        ) {
            install(Persistence) {
                storage = snapshotProvider
                enableAutomaticPersistence = true
            }

            install(EventHandler) {
                onToolCallFailed {
                    throw Exception("Tool call failed")
                }
            }
        }

        try {
            val result: String = agent.run("5 + 3 - 2")
            println("First run result: $result")
        } catch (e: Exception) {
            println("Caught exception as expected: ${e.message}")
        }

        val checkpoints = snapshotProvider.getCheckpoints(agentId)
        println("Snapshot provider state after first run: $checkpoints")

        val agent2 = AIAgent(
            id = agent.id,
            promptExecutor = executor,
            llmModel = OllamaModels.Meta.LLAMA_3_2,
            toolRegistry = correctToolRegistry,
            strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
            systemPrompt = "You are a calculator. Use tools to calculate asked to result.",
            temperature = 0.0,
        ) {
            install(Persistence) {
                storage = snapshotProvider
                enableAutomaticPersistence = true
            }
        }

        val result: String = agent2.run("5 + 3 - 2")
        println("Second run result: $result")
    }
}
