package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.AIAgentStrategy
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
import kotlin.test.assertTrue

class ParallelNodesTest {

    companion object {
        private const val NODE_1 = "node1"
        private const val NODE_2 = "node2"
        private const val NODE_3 = "node3"
    }

    private fun createBaseAgentConfig(promptName: String, promptContent: String): AIAgentConfig {
        return AIAgentConfig(
            prompt = prompt(promptName) { user(promptContent) },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )
    }

    private fun createMockExecutor() = getMockExecutor {
        mockLLMAnswer("Default test response").asDefaultResponse
    }

    private fun createToolRegistry() = ToolRegistry.Companion {
        tool(DummyTool())
    }

    private suspend fun runStringAgent(strategy: AIAgentStrategy<String, String>, config: AIAgentConfig): String {
        val runner = AIAgent<String, String>(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = config,
            toolRegistry = createToolRegistry()
        )
        return runner.run("")
    }

    @Test
    fun testContextIsolation() = runTest {
        val agentStrategy = strategy<String, String>("test-isolation") {
            val testKey1 = AIAgentStorageKey<String>("testKey1")
            val testKey2 = AIAgentStorageKey<String>("testKey2")
            val testKey3 = AIAgentStorageKey<String>("testKey3")
            val val1 = "value1"
            val val2 = "value2"
            val val3 = "value3"

            val additionalText = "Additional text from node2"

            val node1 by node<Unit, String>(NODE_1) {
                storage.set(testKey1, val1)
                "Result from $NODE_1"
            }

            val node2 by node<Unit, String>(NODE_2) {
                llm.writeSession {
                    updatePrompt { user(additionalText) }
                }
                storage.set(testKey2, val2)
                "Result from $NODE_2"
            }

            val node3 by node<Unit, String>(NODE_3) {
                storage.set(testKey3, val3)
                "Result from $NODE_3"
            }

            val parallelNode by parallel(
                node1, node2, node3,
                name = "parallelNode",
            ) {
                val output = results.map {
                    // This node should only see the changes from its own execution
                    val value1 = it.nodeResult.context.storage.get(testKey1)
                    val value2 = it.nodeResult.context.storage.get(testKey2)
                    val value3 = it.nodeResult.context.storage.get(testKey3)

                    var promptModified = false
                    it.nodeResult.context.llm.readSession {
                        promptModified = prompt.toString().contains(additionalText)
                    }

                    if (it.nodeName == NODE_1 && value2 == val2) {
                        return@map "Incorrect: $NODE_1 sees changes of $NODE_2 (value2=${val2})"
                    }
                    if (it.nodeName == NODE_1 && value3 == val3) {
                        return@map "Incorrect: $NODE_1 sees changes of $NODE_3 (value3=${val3})"
                    }
                    if (it.nodeName == NODE_1 && promptModified) {
                        return@map "Incorrect: $NODE_1 sees prompt changes of $NODE_2"
                    }

                    if (it.nodeName == NODE_2 && value1 == val1) {
                        return@map "Incorrect: $NODE_2 sees changes of $NODE_1 (value1=${val1})"
                    }
                    if (it.nodeName == NODE_2 && value3 == val3) {
                        return@map "Incorrect: $NODE_2 sees changes of $NODE_3 (value3=${val3})"
                    }
                    if (it.nodeName == NODE_2 && !promptModified) {
                        return@map "Incorrect: $NODE_2 does not see its own prompt changes"
                    }

                    if (it.nodeName == NODE_3 && value1 == val1) {
                        return@map "Incorrect: $NODE_3 sees changes of $NODE_1 (value1=${val1})"
                    }
                    if (it.nodeName == NODE_3 && value2 == val2) {
                        return@map "Incorrect: $NODE_3 sees changes of $NODE_2 (value2=${val2})"
                    }
                    if (it.nodeName == NODE_3 && promptModified) {
                        return@map "Incorrect: $NODE_3 sees prompt changes of $NODE_2"
                    }

                    "Correct: Node ${it.nodeName} sees no changes from other nodes"
                }.joinToString("\n")

                ParallelNodeExecutionResult(output, this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-prompt", "Base prompt content")
        val result = runStringAgent(agentStrategy, agentConfig)
        assertFalse(result.contains("Incorrect"))
    }

    @Test
    fun testSelectBy() = runTest {
        val agentStrategy = strategy<String, String>("test-select-by") {
            val node1 by node<Unit, String>(NODE_1) { "10" }
            val node2 by node<Unit, String>(NODE_2) { "20" }
            val node3 by node<Unit, String>(NODE_3) { "15" }

            val parallelNode by parallel(
                node1, node2, node3,
                name = "selectByParallel"
            ) {
                val selected = selectBy { output -> output.contains("2") }
                ParallelNodeExecutionResult("Selected: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-select-by", "Test select by")
        val result = runStringAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Selected: 20"))
    }

    @Test
    fun testSelectByMax() = runTest {
        val agentStrategy = strategy<String, String>("test-select-by-max") {
            val node1 by node<Unit, String>(NODE_1) { "apple" }
            val node2 by node<Unit, String>(NODE_2) { "zebra" }
            val node3 by node<Unit, String>(NODE_3) { "banana" }

            val parallelNode by parallel(
                node1, node2, node3,
                name = "selectByMaxParallel"
            ) {
                val selected = selectByMax { output -> output.length }
                ParallelNodeExecutionResult("Max: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-select-by-max", "Test select by max")
        val result = runStringAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Max: banana"))
    }

    @Test
    fun testSelectByIndex() = runTest {
        val agentStrategy = strategy<String, String>("test-select-by-index") {
            val node1 by node<Unit, String>(NODE_1) { "first" }
            val node2 by node<Unit, String>(NODE_2) { "second" }
            val node3 by node<Unit, String>(NODE_3) { "third" }

            val parallelNode by parallel(
                node1, node2, node3,
                name = "selectByIndexParallel"
            ) {
                val selected = selectByIndex { outputs -> 1 }
                ParallelNodeExecutionResult("Selected: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-select-by-index", "Test select by index")
        val result = runStringAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Selected: second"))
    }

    @Test
    fun testFold() = runTest {
        val agentStrategy = strategy<String, String>("test-fold") {
            val node1 by node<Unit, String>(NODE_1) { "Hello" }
            val node2 by node<Unit, String>(NODE_2) { " " }
            val node3 by node<Unit, String>(NODE_3) { "World" }

            val parallelNode by parallel(
                node1, node2, node3,
                name = "foldParallel"
            ) {
                val folded = fold("") { acc, result -> acc + result }
                ParallelNodeExecutionResult("Result: ${folded.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-fold", "Test fold")
        val result = runStringAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Result: Hello World"))
    }

}
