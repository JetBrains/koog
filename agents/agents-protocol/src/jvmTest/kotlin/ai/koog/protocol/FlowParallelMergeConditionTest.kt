package ai.koog.protocol

import ai.koog.agents.core.dsl.builder.ParallelNodeExecutionResult
import ai.koog.agents.core.dsl.builder.ParallelResult
import ai.koog.agents.testing.tools.AIAgentContextMockBuilder
import ai.koog.agents.testing.tools.DummyAIAgentContext
import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.agent.agents.parallel.ParallelMergeCondition
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.flow.KoogStrategyFactory
import kotlin.test.Test
import kotlin.test.assertEquals

class FlowParallelMergeConditionTest {

    private fun evaluateMergeCondition(
        results: List<ParallelResult<*, *>>,
        condition: ParallelMergeCondition
    ): FlowDataType {
        // Use reflection to access the private evaluateMergeCondition method
        val method = KoogStrategyFactory::class.java.getDeclaredMethod(
            "evaluateMergeCondition",
            List::class.java,
            ParallelMergeCondition::class.java
        )
        method.isAccessible = true
        return method.invoke(KoogStrategyFactory, results, condition) as FlowDataType
    }

    private fun createMockResult(
        name: String,
        input: FlowDataType,
        output: FlowDataType
    ): ParallelResult<FlowDataType, FlowDataType> {
        return ParallelResult(
            nodeName = name,
            nodeInput = input,
            nodeResult = ParallelNodeExecutionResult(output, DummyAIAgentContext(AIAgentContextMockBuilder()))
        )
    }

    //region Test: results.X.output

    @Test
    fun testMergeCondition_resultsOutputEquals_string() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input1"), FlowDataType.FlowString("first answer")),
            createMockResult("agent2", FlowDataType.FlowString("input2"), FlowDataType.FlowString("best answer")),
            createMockResult("agent3", FlowDataType.FlowString("input3"), FlowDataType.FlowString("third answer"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1.output",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("best answer")
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowString("best answer"), result)
    }

    @Test
    fun testMergeCondition_resultsOutputEquals_integer() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowInteger(0), FlowDataType.FlowInteger(10)),
            createMockResult("agent2", FlowDataType.FlowInteger(0), FlowDataType.FlowInteger(42)),
            createMockResult("agent3", FlowDataType.FlowInteger(0), FlowDataType.FlowInteger(100))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1.output",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowInteger(42)
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowInteger(42), result)
    }

    @Test
    fun testMergeCondition_resultsOutputMore() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowInteger(0), FlowDataType.FlowInteger(10)),
            createMockResult("agent2", FlowDataType.FlowInteger(0), FlowDataType.FlowInteger(100)),
            createMockResult("agent3", FlowDataType.FlowInteger(0), FlowDataType.FlowInteger(50))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1.output",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowInteger(50)
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowInteger(100), result)
    }

    @Test
    fun testMergeCondition_resultsOutputLess() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowDouble(0.0), FlowDataType.FlowDouble(3.14)),
            createMockResult("agent2", FlowDataType.FlowDouble(0.0), FlowDataType.FlowDouble(1.5)),
            createMockResult("agent3", FlowDataType.FlowDouble(0.0), FlowDataType.FlowDouble(5.0))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1.output",
            operation = ConditionOperationKind.LESS,
            value = FlowDataType.FlowDouble(2.0)
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowDouble(1.5), result)
    }

    //endregion

    //region Test: results.X.input

    @Test
    fun testMergeCondition_resultsInputEquals() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("task1"), FlowDataType.FlowString("output1")),
            createMockResult("agent2", FlowDataType.FlowString("important task"), FlowDataType.FlowString("output2")),
            createMockResult("agent3", FlowDataType.FlowString("task3"), FlowDataType.FlowString("output3"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1.input",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("important task")
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowString("output2"), result)
    }

    //endregion

    //region Test: results.X.name

    @Test
    fun testMergeCondition_resultsNameEquals() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input1"), FlowDataType.FlowString("output1")),
            createMockResult("best_agent", FlowDataType.FlowString("input2"), FlowDataType.FlowString("output2")),
            createMockResult("agent3", FlowDataType.FlowString("input3"), FlowDataType.FlowString("output3"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1.name",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("best_agent")
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowString("output2"), result)
    }

    //endregion

    //region Test: Error Cases

    private fun assertErrorMessage(condition: ParallelMergeCondition, results: List<ParallelResult<*, *>>, expectedMessageSubstring: String) {
        try {
            evaluateMergeCondition(results, condition)
            throw AssertionError("Expected IllegalStateException to be thrown")
        } catch (e: Exception) {
            // The reflection call wraps exceptions in InvocationTargetException
            val cause = e.cause ?: e
            assert(cause is IllegalStateException) { "Expected IllegalStateException but got ${cause::class.simpleName}" }
            assert(cause.message!!.contains(expectedMessageSubstring)) {
                "Expected message to contain '$expectedMessageSubstring' but was '${cause.message}'"
            }
        }
    }

    @Test
    fun testMergeCondition_invalidFormat_missingParts() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("output"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.1",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("value")
        )

        assertErrorMessage(condition, results, "Expected format for merge condition variable")
    }

    @Test
    fun testMergeCondition_invalidFormat_wrongPrefix() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("output"))
        )

        val condition = ParallelMergeCondition(
            variable = "output.1.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("value")
        )

        assertErrorMessage(condition, results, "Expected 'results' keyword")
    }

    @Test
    fun testMergeCondition_invalidIndex_notANumber() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("output"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.abc.output",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("value")
        )

        assertErrorMessage(condition, results, "Invalid index")
    }

    @Test
    fun testMergeCondition_invalidIndex_outOfBounds() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("output"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.5.output",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("value")
        )

        assertErrorMessage(condition, results, "Index out of bounds")
    }

    @Test
    fun testMergeCondition_unsupportedProperty() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("output"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.0.unknown",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("value")
        )

        assertErrorMessage(condition, results, "Unsupported property")
    }

    @Test
    fun testMergeCondition_conditionNotSatisfied() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("wrong output"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.0.output",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("expected output")
        )

        assertErrorMessage(condition, results, "Merge condition not satisfied")
    }

    //endregion

    //region Test: Different Operations

    @Test
    fun testMergeCondition_booleanEquals() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowBoolean(false), FlowDataType.FlowBoolean(true)),
            createMockResult("agent2", FlowDataType.FlowBoolean(false), FlowDataType.FlowBoolean(false))
        )

        val condition = ParallelMergeCondition(
            variable = "results.0.output",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowBoolean(true)
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowBoolean(true), result)
    }

    @Test
    fun testMergeCondition_notEquals() {
        val results = listOf(
            createMockResult("agent1", FlowDataType.FlowString("input"), FlowDataType.FlowString("different")),
            createMockResult("agent2", FlowDataType.FlowString("input"), FlowDataType.FlowString("same"))
        )

        val condition = ParallelMergeCondition(
            variable = "results.0.output",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowDataType.FlowString("same")
        )

        val result = evaluateMergeCondition(results, condition)

        assertEquals(FlowDataType.FlowString("different"), result)
    }

    //endregion
}
