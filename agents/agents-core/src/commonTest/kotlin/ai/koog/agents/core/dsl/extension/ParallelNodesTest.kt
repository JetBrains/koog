package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.dsl.builder.ForkResult
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
import kotlin.test.assertFalse
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
            val parallelNode by fork<Unit, String>(
                node1, node2, node3,
                name = "parallelNode",
            )

            val reduceNode by merge<Unit, String>(name = "reduceNode") { results ->
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

    @Test
    fun testContextIsolation() = runTest {
        // Create keys to store and retrieve values from the context
        val testKey1 = AIAgentStorageKey<String>("testKey1")
        val testKey2 = AIAgentStorageKey<String>("testKey2")
        val testKey3 = AIAgentStorageKey<String>("testKey3")

        val basePrompt = prompt("test-prompt") {
            user("Base prompt content")
        }

        val agentStrategy = strategy("test-isolation") {
            // Create three nodes that modify different aspects of the context
            val node1 by node<Unit, String>("node1") {
                // Modify storage
                storage.set(testKey1, "value1")
                "Result from node1"
            }

            val node2 by node<Unit, String>("node2") {
                // Modify prompt
                llm.writeSession {
                    updatePrompt { user("Additional text from node2") }
                }
                storage.set(testKey2, "value2")
                "Result from node2"
            }

            val node3 by node<Unit, String>("node3") {
                // Modify storage with a different key
                storage.set(testKey3, "value3")
                "Result from node3"
            }

            // Create a parallel node that executes all three nodes
            val parallelNode by fork(
                node1, node2, node3,
                name = "parallelNode",
            )

            // Create nodes to verify the context isolation during parallel execution
            val verifyNode by node<List<ForkResult<Unit, String>>, String>("verifyNode") { results ->
                results.map {
                    // This node should only see the changes from node1
                    val value1 = it.context.storage.get(testKey1)
                    val value2 = it.context.storage.get(testKey2)
                    val value3 = it.context.storage.get(testKey3)

                    var promptModified = false
                    it.context.llm.readSession {
                        promptModified = prompt.toString().contains("Additional text from node2")
                    }

                    // Node 1 checks
                    if (it.nodeName == "node1" && value2 != null) {
                        return@map "Incorrect: node1 sees changes of node2 (value2=${value2})"
                    }
                    if (it.nodeName == "node1" && value3 != null) {
                        return@map "Incorrect: node1 sees changes of node3 (value3=${value3})"
                    }
                    if (it.nodeName == "node1" && promptModified) {
                        return@map "Incorrect: node1 sees prompt changes of node2"
                    }

                    // Node 2 checks
                    if (it.nodeName == "node2" && value1 != null) {
                        return@map "Incorrect: node2 sees changes of node1 (value1=${value1})"
                    }
                    if (it.nodeName == "node2" && value3 != null) {
                        return@map "Incorrect: node2 sees changes of node3 (value3=${value3})"
                    }
                    if (it.nodeName == "node2" && !promptModified) {
                        return@map "Incorrect: node2 does not see its own prompt changes"
                    }

                    // Node 3 checks
                    if (it.nodeName == "node3" && value1 != null) {
                        return@map "Incorrect: node3 sees changes of node1 (value1=${value1})"
                    }
                    if (it.nodeName == "node3" && value2 != null) {
                        return@map "Incorrect: node3 sees changes of node2 (value2=${value2})"
                    }
                    if (it.nodeName == "node3" && promptModified) {
                        return@map "Incorrect: node3 sees prompt changes of node2"
                    }

                    "Correct: Node ${it.nodeName} sees no changes from other nodes"
                }.joinToString("\n")
            }

            // Connect the nodes
            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo verifyNode)
            edge(verifyNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = basePrompt,
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
        }

        val result = runner.runAndGetResult("")

        assertFalse(result?.contains("Incorrect") ?: false)
    }
}
