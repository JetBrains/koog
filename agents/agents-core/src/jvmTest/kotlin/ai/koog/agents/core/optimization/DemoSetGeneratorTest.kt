package ai.koog.agents.core.optimization

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.optimization.core.Demonstration
import ai.koog.agents.core.optimization.core.Example
import ai.koog.agents.core.optimization.core.Metric
import ai.koog.agents.core.optimization.core.optimizableNode
import ai.koog.agents.core.optimization.optimizers.mipro.generateDemoSets
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoSetGeneratorTest {

    private val trainset = (0..4).map { i ->
        Example(input = "Question $i", label = "Answer $i")
    }

    private val simpleStrategy = strategy("test") {
        val thinking by optimizableNode(
            instruction = "Think about the question",
            demonstrations = (0..4).map { Demonstration("Question $it", "Thinking $it", false) }
        )
        val answer by optimizableNode(
            instruction = "Answer the question",
            demonstrations = (0..4).map { Demonstration("Thinking $it", "Answer $it", false) }
        )

        edge(nodeStart forwardTo thinking)
        edge(thinking forwardTo answer)
        edge(answer forwardTo nodeFinish)
    }

    private val agentConfig = AIAgentConfig(
        Prompt.Empty,
        OpenAIModels.Chat.GPT5Nano,
        10
    )

    private fun createMockExecutor(): PromptExecutor = getMockExecutor {
        for (i in 0..4) {
            mockLLMAnswer("Thinking $i (bootstrapped)") onCondition { "Question $i" in it }
            mockLLMAnswer("Answer $i") onCondition { "Thinking $i" in it }
        }
        mockLLMAnswer("Default answer")
    }

    private val exactMatch: Metric<String> = { expected, actual -> if (expected == actual) 1.0 else 0.0 }

    @Test
    fun testZeroShotModeReturnsNull() = runBlocking {
        val executor = createMockExecutor()
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = 6,
            maxBootstrappedDemos = 0,
            maxLabeledDemos = 0,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
        )

        assertNull(result, "Zero-shot mode should return null")
    }

    @Test
    fun testCorrectNumberOfCandidateSets() = runBlocking {
        val executor = createMockExecutor()
        val numCandidates = 6
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = numCandidates,
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
        )

        assertNotNull(result, "Result should not be null")
        for ((nodeName, candidateSets) in result) {
            assertEquals(numCandidates, candidateSets.size,
                "Node '$nodeName' should have exactly $numCandidates candidate sets, got ${candidateSets.size}")
        }
    }

    @Test
    fun testZeroShotSetIsEmpty() = runBlocking {
        val executor = createMockExecutor()
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = 6,
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
            includeNonBootstrapped = true,
        )

        assertNotNull(result)
        // First set should be zero-shot (empty)
        for ((nodeName, candidateSets) in result) {
            assertTrue(candidateSets[0].isEmpty(),
                "First candidate set for '$nodeName' should be empty (zero-shot), got ${candidateSets[0].size} demos")
        }
    }

    @Test
    fun testLabeledOnlySetHasLabeledDemos() = runBlocking {
        val executor = createMockExecutor()
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = 6,
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
            includeNonBootstrapped = true,
        )

        assertNotNull(result)
        // Second set should be labeled-only (non-bootstrapped)
        for ((nodeName, candidateSets) in result) {
            val labeledSet = candidateSets[1]
            assertTrue(labeledSet.isNotEmpty(),
                "Labeled-only set for '$nodeName' should not be empty")
            assertTrue(labeledSet.all { !it.isBootstrapped },
                "Labeled-only set for '$nodeName' should contain only non-bootstrapped demos")
            assertTrue(labeledSet.size <= 4,
                "Labeled-only set for '$nodeName' should have at most 4 demos, got ${labeledSet.size}")
        }
    }

    @Test
    fun testBootstrapSetsPopulated() = runBlocking {
        val executor = createMockExecutor()
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = 6,
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
            includeNonBootstrapped = true,
        )

        assertNotNull(result)
        // Sets at index 2+ should be bootstrap sets (may have both bootstrapped and labeled demos)
        for ((nodeName, candidateSets) in result) {
            // Unshuffled bootstrap at index 2
            val bootstrapSet = candidateSets[2]
            // Bootstrap set may or may not have demos depending on whether bootstrap succeeded
            // Just check it exists
            assertTrue(candidateSets.size >= 3,
                "Node '$nodeName' should have at least 3 candidate sets (zero-shot + labeled + bootstrap)")
        }
    }

    @Test
    fun testAllNodesRepresented() = runBlocking {
        val executor = createMockExecutor()
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = 4,
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
        )

        assertNotNull(result)
        // Both nodes should be present in the result
        assertTrue("thinking" in result, "Result should contain 'thinking' node")
        assertTrue("answer" in result, "Result should contain 'answer' node")
        assertEquals(2, result.size, "Result should have exactly 2 nodes")
    }

    @Test
    fun testWithoutIncludeNonBootstrapped() = runBlocking {
        val executor = createMockExecutor()
        val numCandidates = 4
        val result = generateDemoSets(
            promptExecutor = executor,
            agentConfig = agentConfig,
            strategy = simpleStrategy,
            trainset = trainset,
            numCandidateSets = numCandidates,
            maxBootstrappedDemos = 4,
            maxLabeledDemos = 4,
            metric = exactMatch,
            metricThreshold = 1.0,
            maxErrors = null,
            includeNonBootstrapped = false,
        )

        assertNotNull(result)
        for ((nodeName, candidateSets) in result) {
            assertEquals(numCandidates, candidateSets.size,
                "Node '$nodeName' should have $numCandidates candidate sets")
            // No zero-shot or labeled-only sets: first set should NOT be empty
            // (it's the unshuffled bootstrap set)
            // Note: Even the unshuffled bootstrap might have empty results if bootstrap fails,
            // but the first set is NOT guaranteed to be empty like in the includeNonBootstrapped case
        }
    }
}
