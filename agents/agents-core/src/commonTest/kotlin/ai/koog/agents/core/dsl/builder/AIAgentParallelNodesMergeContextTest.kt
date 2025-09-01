package ai.koog.agents.core.dsl.builder

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.testing.tools.AIAgentContextMockBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalAgentsApi::class)
class AIAgentParallelNodesMergeContextTest {

    private fun createMockContext(): AIAgentContextBase {
        return AIAgentContextMockBuilder().apply {
            runId = "test-run-id"
            strategyName = "test-strategy"
        }.build()
    }

    private fun createParallelResults(): List<ParallelResult<String, Int>> {
        val mockContext1 = createMockContext()
        val mockContext2 = createMockContext()
        val mockContext3 = createMockContext()

        return listOf(
            ParallelResult("node1", "input", ParallelNodeExecutionResult(10, mockContext1)),
            ParallelResult("node2", "input", ParallelNodeExecutionResult(30, mockContext2)),
            ParallelResult("node3", "input", ParallelNodeExecutionResult(20, mockContext3))
        )
    }

    @Test
    fun testContextDelegation() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        assertEquals(underlyingContext.agentId, mergeContext.agentId)
        assertEquals(underlyingContext.runId, mergeContext.runId)
        assertEquals(underlyingContext.strategyName, mergeContext.strategyName)
    }

    @Test
    fun testSelectByWithCustomPredicate() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        val selected = mergeContext.selectBy { output -> output % 10 == 0 }
        assertEquals(10, selected.output)
    }

    @Test
    fun testSelectByWithNoMatch() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        assertFailsWith<NoSuchElementException> {
            mergeContext.selectBy { output -> output > 100 }
        }
    }

    @Test
    fun testSelectByMaxWithStringComparison() = runTest {
        val underlyingContext = createMockContext()

        val stringResults = listOf(
            ParallelResult("node1", "input", ParallelNodeExecutionResult("apple", createMockContext())),
            ParallelResult("node2", "input", ParallelNodeExecutionResult("zebra", createMockContext())),
            ParallelResult("node3", "input", ParallelNodeExecutionResult("banana", createMockContext()))
        )

        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, stringResults)

        // Select by string length
        val selectedByLength = mergeContext.selectByMax { output -> output.length }
        assertEquals("banana", selectedByLength.output)

        // Select by alphabetical order
        val selectedAlphabetically = mergeContext.selectByMax { output -> output }
        assertEquals("zebra", selectedAlphabetically.output)
    }

    @Test
    fun testSelectByMaxWithEmptyResults() = runTest {
        val underlyingContext = createMockContext()
        val emptyResults = emptyList<ParallelResult<String, Int>>()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, emptyResults)

        assertFailsWith<NoSuchElementException> {
            mergeContext.selectByMax { output -> output }
        }
    }

    @Test
    fun testSelectByIndexWithValidIndices() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        val first = mergeContext.selectByIndex { outputs -> 0 }
        assertEquals(10, first.output)

        val last = mergeContext.selectByIndex { outputs -> outputs.size - 1 }
        assertEquals(20, last.output)

        val middle = mergeContext.selectByIndex { outputs -> 1 }
        assertEquals(30, middle.output)
    }

    @Test
    fun testSelectByIndexWithInvalidIndex() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        assertFailsWith<IndexOutOfBoundsException> {
            mergeContext.selectByIndex { outputs -> -1 }
        }

        assertFailsWith<IndexOutOfBoundsException> {
            mergeContext.selectByIndex { outputs -> outputs.size }
        }
    }

    @Test
    fun testFoldWithDifferentOperations() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        val sum = mergeContext.fold(0) { acc, result -> acc + result }
        assertEquals(60, sum.output)
        assertEquals(underlyingContext, sum.context)

        val product = mergeContext.fold(1) { acc, result -> acc * result }
        assertEquals(6000, product.output)

        val max = mergeContext.fold(Int.MIN_VALUE) { acc, result -> maxOf(acc, result) }
        assertEquals(30, max.output)

        val stringResults = listOf(
            ParallelResult("node1", "input", ParallelNodeExecutionResult("Hello", createMockContext())),
            ParallelResult("node2", "input", ParallelNodeExecutionResult(" ", createMockContext())),
            ParallelResult("node3", "input", ParallelNodeExecutionResult("World", createMockContext()))
        )
        val stringMergeContext = AIAgentParallelNodesMergeContext(underlyingContext, stringResults)

        val concatenated = stringMergeContext.fold("") { acc, result -> acc + result }
        assertEquals("Hello World", concatenated.output)
    }

    @Test
    fun testFoldWithEmptyResults() = runTest {
        val underlyingContext = createMockContext()
        val emptyResults = emptyList<ParallelResult<String, Int>>()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, emptyResults)

        // Fold should work with empty results and return the initial value
        val result = mergeContext.fold(42) { acc, result -> acc + result }
        assertEquals(42, result.output)
        assertEquals(underlyingContext, result.context)
    }

    @Test
    fun testResultsPropertyAccess() = runTest {
        val underlyingContext = createMockContext()
        val results = createParallelResults()
        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, results)

        assertEquals(3, mergeContext.results.size)
        assertEquals("node1", mergeContext.results[0].nodeName)
        assertEquals("node2", mergeContext.results[1].nodeName)
        assertEquals("node3", mergeContext.results[2].nodeName)

        assertEquals(10, mergeContext.results[0].nodeResult.output)
        assertEquals(30, mergeContext.results[1].nodeResult.output)
        assertEquals(20, mergeContext.results[2].nodeResult.output)
    }

    @Test
    fun testComplexSelectScenarios() = runTest {
        val underlyingContext = createMockContext()
        val complexResults = listOf(
            ParallelResult("fast", "input", ParallelNodeExecutionResult(Pair(100, 1), createMockContext())),
            ParallelResult("slow_cheap", "input", ParallelNodeExecutionResult(Pair(50, 10), createMockContext())),
            ParallelResult("balanced", "input", ParallelNodeExecutionResult(Pair(75, 5), createMockContext()))
        )

        val mergeContext = AIAgentParallelNodesMergeContext(underlyingContext, complexResults)

        val fastest = mergeContext.selectByMax { (speed, _) -> speed }
        assertEquals(Pair(100, 1), fastest.output)

        val cheapest = mergeContext.selectByMax { (_, cost) -> -cost }
        assertEquals(Pair(100, 1), cheapest.output)

        val bestRatio = mergeContext.selectByMax { (speed, cost) -> speed.toDouble() / cost }
        assertEquals(Pair(100, 1), bestRatio.output)
    }
}
