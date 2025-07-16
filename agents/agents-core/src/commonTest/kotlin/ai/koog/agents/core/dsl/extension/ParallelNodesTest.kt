package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.dsl.builder.ParallelNodeExecutionResult
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class ParallelNodesTest {

    @Test
    fun testContextIsolation() = runTest {
        // Create keys to store and retrieve values from the context
        val testKey1 = AIAgentStorageKey<String>("testKey1")
        val testKey2 = AIAgentStorageKey<String>("testKey2")
        val testKey3 = AIAgentStorageKey<String>("testKey3")

        val basePrompt = prompt("test-prompt") {
            user("Base prompt content")
        }

        val agentStrategy = strategy<String, String>("test-isolation") {
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
            val parallelNode by parallel(
                node1, node2, node3,
                name = "parallelNode",
            ) {
                val output = results.map {
                    // This node should only see the changes from node1
                    val value1 = it.nodeResult.context.storage.get(testKey1)
                    val value2 = it.nodeResult.context.storage.get(testKey2)
                    val value3 = it.nodeResult.context.storage.get(testKey3)

                    var promptModified = false
                    it.nodeResult.context.llm.readSession {
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

                ParallelNodeExecutionResult(output, this)
            }

            // Connect the nodes
            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = AIAgentConfig(
            prompt = basePrompt, model = OllamaModels.Meta.LLAMA_3_2, maxAgentIterations = 10
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
            })

        val result = runner.run("")

        assertFalse(result.contains("Incorrect"))
    }
}
