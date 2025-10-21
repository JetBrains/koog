package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.dsl.builder.AIAgentGraphStrategyBuilder
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParallelNodesMergeContextTest {

    private val testKey = AIAgentStorageKey<String>("testKey")

    private fun AIAgentGraphStrategyBuilder<String, String>.testNode(
        name: String,
        value: String
    ): AIAgentNodeDelegate<Unit, String> {
        return node(name) {
            storage.set(testKey, value)
            "Output from $name is value $value"
        }
    }

    suspend fun runAgent(strategy: AIAgentGraphStrategy<String, String>): String? {
        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {},
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val testExecutor = getMockExecutor {
            mockLLMAnswer("Default test response").asDefaultResponse
        }

        val agent = AIAgent(
            promptExecutor = testExecutor,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry.Companion {
                tool(DummyTool())
            }
        )

        return agent.run("")
    }

    @Test
    fun testMergeFold() = runTest {
        val agentStrategy = strategy("test-context") {
            val node1 by testNode("node1", "value1")
            val node2 by testNode("node2", "value2")
            val node3 by testNode("node3", "value3")

            val nodeParallel by parallel(node1, node2, node3) {
                fold("All results:") { acc, output -> acc + "\n" + output }
            }

            edge(nodeStart forwardTo nodeParallel transformed { })
            edge(nodeParallel forwardTo nodeFinish)
        }

        val result = runAgent(agentStrategy)
        assertNotNull(result)
        assertEquals(
            """
                All results:
                Output from node1 is value value1
                Output from node2 is value value2
                Output from node3 is value value3
            """.trimIndent(),
            result
        )
    }

    @Test
    fun testMergeSelectBy() = runTest {
        val agentStrategy = strategy("test-context") {
            val node1 by testNode("node1", "value1")
            val node2 by testNode("node2", "value2")
            val node3 by testNode("node3", "value3")

            val nodeParallel by parallel(node1, node2, node3) {
                selectBy { output -> output.contains("node2") }
            }

            // To check that the context for node 2 was selected in mergeNode
            val nodeTransformResult by node<String, String> {
                "$it and storage value ${storage.get(testKey)}"
            }

            edge(nodeStart forwardTo nodeParallel transformed { })
            edge(nodeParallel forwardTo nodeTransformResult)
            edge(nodeTransformResult forwardTo nodeFinish)
        }

        val result = runAgent(agentStrategy)
        assertNotNull(result)
        assertEquals("Output from node2 is value value2 and storage value value2", result)
    }

    @Test
    fun testMergeSelectByIndex() = runTest {
        val agentStrategy = strategy("test-context") {
            val node1 by testNode("node1", "value1")
            val node2 by testNode("node2", "value2")
            val node3 by testNode("node3", "value3")

            val nodeParallel by parallel(node1, node2, node3) {
                selectByIndex { 1 }
            }

            // To check that the context for node 2 was selected in mergeNode
            val nodeTransformResult by node<String, String> {
                "$it and storage value ${storage.get(testKey)}"
            }

            edge(nodeStart forwardTo nodeParallel transformed { })
            edge(nodeParallel forwardTo nodeTransformResult)
            edge(nodeTransformResult forwardTo nodeFinish)
        }

        val result = runAgent(agentStrategy)
        assertNotNull(result)
        assertEquals("Output from node2 is value value2 and storage value value2", result)
    }

    @Test
    fun testMergeSelectMax() = runTest {
        val agentStrategy = strategy("test-context") {
            val node1 by testNode("node1", "value1")
            val node2 by testNode("node2", "value2")
            val node3 by testNode("node3", "value3")

            val nodeParallel by parallel(node1, node2, node3) {
                selectByMax { it }
            }

            // To check that the context for node 2 was selected in mergeNode
            val nodeTransformResult by node<String, String> {
                "$it and storage value ${storage.get(testKey)}"
            }

            edge(nodeStart forwardTo nodeParallel transformed { })
            edge(nodeParallel forwardTo nodeTransformResult)
            edge(nodeTransformResult forwardTo nodeFinish)
        }

        val result = runAgent(agentStrategy)
        assertNotNull(result)
        assertEquals("Output from node3 is value value3 and storage value value3", result)
    }
}
