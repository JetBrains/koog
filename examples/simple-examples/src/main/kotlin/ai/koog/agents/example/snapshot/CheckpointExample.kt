package ai.koog.agents.example.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.calculator.CalculatorTools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.snapshot.feature.Persistency
import ai.koog.agents.snapshot.providers.InMemoryPersistencyStorageProvider
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun main() = runBlocking {
    val executor: PromptExecutor = simpleOllamaAIExecutor()

    val brokenToolRegistry = ToolRegistry {
        tools(BrokenCalculatorTools().asTools())
    }

    val correctToolRegistry = ToolRegistry {
        tools(CalculatorTools().asTools())
    }

    val persistenceId = "snapshot-agent-example"

    val snapshotProvider = InMemoryPersistencyStorageProvider(
        persistenceId = persistenceId
    )
    val agent = AIAgent(
        promptExecutor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        toolRegistry = brokenToolRegistry,
        systemPrompt = "You are a calculator. Use tools to calculate asked to result.",
        temperature = 0.0,
    ) {
        install(Persistency) {
            storage = snapshotProvider
            enableAutomaticPersistency = true
        }

        install(EventHandler) {
            onToolCallFailure {
                throw Exception("Tool call failed")
            }
        }
    }

    runBlocking {
        try {
            val result: String = agent.run("5 + 3 - 2")
            println("First run result: $result")
        } catch (e: Exception) {
            println("Caught exception as expected: ${e.message}")
        }
    }

    val checkpoints = snapshotProvider.getCheckpoints()
    println("Snapshot provider state after first run: $checkpoints")

    val agent2 = AIAgent(
        promptExecutor = executor,
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        toolRegistry = correctToolRegistry,
        strategy = singleRunStrategy(ToolCalls.SEQUENTIAL),
        systemPrompt = "You are a calculator. Use tools to calculate asked to result.",
        temperature = 0.0,
        id = agent.id
    ) {
        install(Persistency) {
            storage = snapshotProvider
            enableAutomaticPersistency = true
        }
    }

    val result: String = agent2.run("5 + 3 - 2")
    println("Second run result: $result")
}
