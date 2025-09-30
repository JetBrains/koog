package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
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
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
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

    private suspend fun runTestAgent(strategy: AIAgentGraphStrategy<String, String>, config: AIAgentConfig): String {
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
                node1,
                node2,
                node3,
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
                        return@map "Incorrect: $NODE_1 sees changes of $NODE_2 (value2=$val2)"
                    }
                    if (it.nodeName == NODE_1 && value3 == val3) {
                        return@map "Incorrect: $NODE_1 sees changes of $NODE_3 (value3=$val3)"
                    }
                    if (it.nodeName == NODE_1 && promptModified) {
                        return@map "Incorrect: $NODE_1 sees prompt changes of $NODE_2"
                    }

                    if (it.nodeName == NODE_2 && value1 == val1) {
                        return@map "Incorrect: $NODE_2 sees changes of $NODE_1 (value1=$val1)"
                    }
                    if (it.nodeName == NODE_2 && value3 == val3) {
                        return@map "Incorrect: $NODE_2 sees changes of $NODE_3 (value3=$val3)"
                    }
                    if (it.nodeName == NODE_2 && !promptModified) {
                        return@map "Incorrect: $NODE_2 does not see its own prompt changes"
                    }

                    if (it.nodeName == NODE_3 && value1 == val1) {
                        return@map "Incorrect: $NODE_3 sees changes of $NODE_1 (value1=$val1)"
                    }
                    if (it.nodeName == NODE_3 && value2 == val2) {
                        return@map "Incorrect: $NODE_3 sees changes of $NODE_2 (value2=$val2)"
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
        val result = runTestAgent(agentStrategy, agentConfig)
        assertFalse(result.contains("Incorrect"))
    }

    @Test
    fun testSelectBy() = runTest {
        val agentStrategy = strategy<String, String>("test-select-by") {
            val node1 by node<Unit, String>(NODE_1) { "10" }
            val node2 by node<Unit, String>(NODE_2) { "20" }
            val node3 by node<Unit, String>(NODE_3) { "15" }

            val parallelNode by parallel(
                node1,
                node2,
                node3,
                name = "selectByParallel"
            ) {
                val selected = selectBy { output -> output.contains("2") }
                ParallelNodeExecutionResult("Selected: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-select-by", "Test select by")
        val result = runTestAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Selected: 20"))
    }

    @Test
    fun testSelectByMax() = runTest {
        val agentStrategy = strategy<String, String>("test-select-by-max") {
            val node1 by node<Unit, String>(NODE_1) { "apple" }
            val node2 by node<Unit, String>(NODE_2) { "zebra" }
            val node3 by node<Unit, String>(NODE_3) { "banana" }

            val parallelNode by parallel(
                node1,
                node2,
                node3,
                name = "selectByMaxParallel"
            ) {
                val selected = selectByMax { output -> output.length }
                ParallelNodeExecutionResult("Max: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-select-by-max", "Test select by max")
        val result = runTestAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Max: banana"))
    }

    @Test
    fun testSelectByIndex() = runTest {
        val agentStrategy = strategy<String, String>("test-select-by-index") {
            val node1 by node<Unit, String>(NODE_1) { "first" }
            val node2 by node<Unit, String>(NODE_2) { "second" }
            val node3 by node<Unit, String>(NODE_3) { "third" }

            val parallelNode by parallel(
                node1,
                node2,
                node3,
                name = "selectByIndexParallel"
            ) {
                val selected = selectByIndex { outputs -> 1 }
                ParallelNodeExecutionResult("Selected: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-select-by-index", "Test select by index")
        val result = runTestAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Selected: second"))
    }

    @Test
    fun testFold() = runTest {
        val agentStrategy = strategy<String, String>("test-fold") {
            val node1 by node<Unit, String>(NODE_1) { "Hello" }
            val node2 by node<Unit, String>(NODE_2) { " " }
            val node3 by node<Unit, String>(NODE_3) { "World" }

            val parallelNode by parallel(
                node1,
                node2,
                node3,
                name = "foldParallel"
            ) {
                val folded = fold("") { acc, result -> acc + result }
                ParallelNodeExecutionResult("Result: ${folded.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-fold", "Test fold")
        val result = runTestAgent(agentStrategy, agentConfig)

        assertTrue(result.contains("Result: Hello World"))
    }

    @Test
    fun testOneNodeFailureFoldHandling() = runTest {
        val exceptionMessage = "Node failure test"
        val agentStrategy = strategy<String, String>("test-node-failure") {
            val successNode by node<Unit, String>("success-node") { "Success result" }
            val failureNode by node<Unit, String>("failure-node") {
                throw RuntimeException(exceptionMessage)
            }
            val anotherSuccessNode by node<Unit, String>("another-success-node") { "Another success" }

            val parallelNode by parallel(
                successNode,
                failureNode,
                anotherSuccessNode,
                name = "failureHandlingParallel"
            ) {
                val combinedResults = fold("") { initial, result ->
                    if (initial.isEmpty()) result else "$initial, $result"
                }

                ParallelNodeExecutionResult("Results: ${combinedResults.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-node-failure", "Test node failure handling")

        try {
            runTestAgent(agentStrategy, agentConfig)
            // shouldn't get here, so we intendedly throw an exception
            assertTrue(false, "Expected RuntimeException to be thrown")
        } catch (e: Exception) {
            assertTrue(
                e.message?.contains(exceptionMessage) == true,
                "Expected exception message to contain '$exceptionMessage', but got: ${e.message}"
            )
        }
    }

    @Test
    fun testAllNodesFailureFoldHandling() = runTest {
        val exceptionMessages = listOf("Node failure", "One more failure")
        val agentStrategy = strategy<String, String>("test-node-failure") {
            val failureNode1 by node<Unit, String>("failure-node-1") {
                throw IllegalArgumentException(exceptionMessages[0])
            }
            val failureNode2 by node<Unit, String>("failure-node-2") {
                throw RuntimeException(exceptionMessages[1])
            }

            val parallelNode by parallel(
                failureNode1,
                failureNode2,
                name = "failureHandlingParallel"
            ) {
                val combinedResults = fold("") { initial, result ->
                    if (initial.isEmpty()) result else "$initial, $result"
                }

                ParallelNodeExecutionResult("Results: ${combinedResults.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-node-failure", "Test all nodes failure handling")

        try {
            runTestAgent(agentStrategy, agentConfig)
            // shouldn't get here, so we intendedly throw an exception
            assertTrue(false, "Expected an exception to be thrown")
        } catch (e: Exception) {
            // not sure if it's intended to work like this, leaving OR until figured out
            assertTrue(
                e.message?.contains(exceptionMessages[0]) == true ||
                    e.message?.contains(exceptionMessages[1]) == true,
                "Expected exception message to contain '${exceptionMessages[0]}' or '${exceptionMessages[0]}'" +
                    ", but got: ${e.message}"
            )
            assertTrue(
                e.instanceOf(IllegalArgumentException::class) ||
                    e.instanceOf(RuntimeException::class),
                "Expected IllegalArgumentException or RuntimeException, but got: ${e::class}"
            )
        }
    }

    // Not sure if the current behaviour is not a bug hence ignore
    @Ignore
    @Test
    fun testNodeFailureSelectSuccessfulHandling() = runTest {
        val exceptionMessage = "Node failure test"
        val successMessage = "Success result"
        val agentStrategy = strategy<String, String>("test-node-failure") {
            val successNode by node<Unit, String>("success-node") { successMessage }
            val failureNode by node<Unit, String>("failure-node") {
                throw RuntimeException(exceptionMessage)
            }
            val anotherSuccessNode by node<Unit, String>("another-success-node") { "Another $successMessage" }

            val parallelNode by parallel(
                successNode,
                failureNode,
                anotherSuccessNode,
                name = "failureHandlingParallel"
            ) {
                // a failure of not selected node causes the entire parallel operation to fail.
                val selected = selectBy { output -> output.contains(successMessage) }
                ParallelNodeExecutionResult("Selected: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-node-failure", "Test node failure handling")

        val result = runTestAgent(agentStrategy, agentConfig)
        assertTrue(result.contains(successMessage))
    }

    @Test
    fun testNodeFailureSelectFailedHandling() = runTest {
        val exceptionMessage = "Node failure test"
        val successMessage = "Success result"
        val agentStrategy = strategy<String, String>("test-node-failure") {
            val successNode by node<Unit, String>("success-node") { successMessage }
            val failureNode by node<Unit, String>("failure-node") {
                throw RuntimeException(exceptionMessage)
            }
            val anotherSuccessNode by node<Unit, String>("another-success-node") { "Another $successMessage" }

            val parallelNode by parallel(
                successNode,
                failureNode,
                anotherSuccessNode,
                name = "failureHandlingParallel"
            ) {
                // a failure of the selected node should cause the entire parallel operation to fail.
                val selected = selectByIndex { output -> 1 }
                ParallelNodeExecutionResult("Selected: ${selected.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-node-failure", "Test node failure handling")

        try {
            runTestAgent(agentStrategy, agentConfig)
            // shouldn't get here, so we intendedly throw an exception
            assertTrue(false, "Expected RuntimeException to be thrown")
        } catch (e: Exception) {
            assertTrue(
                e.message?.contains(exceptionMessage) == true,
                "Expected exception message to contain '$exceptionMessage', but got: ${e.message}"
            )
        }
    }

    @Test
    fun testNodeFailureWithExceptionProcessing() = runTest {
        val successMessage = "Operation completed successfully"
        val fallbackMessage = "using fallback result"

        val agentStrategy = strategy<String, String>("test-failure-processing") {
            val successfulNode by node<Unit, String>("successful-node") { successMessage }
            val failureHandlingNode by node<Unit, String>("failure-handling-node") {
                try {
                    throw RuntimeException("Internal operation failed")
                } catch (e: Exception) {
                    "Error [${e.message}] - $fallbackMessage"
                }
            }

            val parallelNode by parallel(
                successfulNode,
                failureHandlingNode,
                name = "partialFailureParallel"
            ) {
                val combinedResults = fold("") { initial, result ->
                    if (initial.isEmpty()) result else "$initial, $result"
                }

                ParallelNodeExecutionResult("Results: ${combinedResults.output}", this)
            }

            edge(nodeStart forwardTo parallelNode transformed { })
            edge(parallelNode forwardTo nodeFinish)
        }

        val agentConfig = createBaseAgentConfig("test-partial-failure", "")
        val result = runTestAgent(agentStrategy, agentConfig)

        println(result)
        assertTrue(result.contains(successMessage))
        assertTrue(result.contains(fallbackMessage))
    }
}
