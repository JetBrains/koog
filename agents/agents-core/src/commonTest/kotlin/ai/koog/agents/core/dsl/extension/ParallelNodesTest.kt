package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelNodesTest {

    @Test
    fun testContextSubstitution() = runTest {
        // Create a key to store and retrieve values from the context
        val testKey = AIAgentStorageKey<String>("testKey")

        val agentStrategy = strategy("test-context") {
            // Create three nodes that modify the context
            val node1 by node<Unit, String>("node1") {
                storage.set(testKey, "value1")
                "Result from node1"
            }

            val node2 by node<Unit, String>("node2") {
                storage.set(testKey, "value2")
                "Result from node2"
            }

            val node3 by node<Unit, String>("node3") {
                storage.set(testKey, "value3")
                "Result from node3"
            }

            // Create a parallel node that executes all three nodes
            val parallelNode by parallel<Unit, String>(
                node1, node2, node3,
                name = "parallelNode",
            )

            val reduceNode by reduce<Unit, String>(name = "reduceNode") { results ->
                // Use the context from the third node (node3)
                val nodeResult = results.find { it.nodeName == "node3" }!!
                nodeResult.context to nodeResult.output
            }

            // Node to verify the context after parallel execution
            val verifyNode by node<String, String>("verifyNode") { input ->
                // The context should have been replaced with node3's context
                val value = storage.get(testKey)
                "$input, context value: $value"
            }

            // Connect the nodes
            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo reduceNode)
            edge(reduceNode forwardTo verifyNode)
            edge(verifyNode forwardTo nodeFinish)
        }

        val results = mutableListOf<String?>()

        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val testExecutor = getMockExecutor {
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        val runner = AIAgent(
            promptExecutor = testExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion {
                tool(DummyTool())
            }
        ) {
            install(EventHandler.Feature) {
                onAgentFinished = { _, result -> results += result }
            }
        }

        runner.run("")

        // Verify that we have one result
        assertEquals(1, results.size)

        // Verify that the context was properly substituted (should contain value3)
        val result = results.first() ?: ""
        assertTrue(
            result.contains("context value: value3"),
            "Result should contain 'context value: value3', but was: $result"
        )
    }
}