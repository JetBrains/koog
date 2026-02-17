package ai.koog.protocol.cli

import ai.koog.protocol.agent.FlowDataType
import kotlin.test.Test
import kotlin.test.assertEquals

class OutputFormatterTest {

    @Test
    fun testFlowString() {
        assertEquals("hello world", OutputFormatter.format(FlowDataType.FlowString("hello world")))
    }

    @Test
    fun testFlowInteger() {
        assertEquals("42", OutputFormatter.format(FlowDataType.FlowInteger(42)))
    }

    @Test
    fun testFlowDouble() {
        assertEquals("3.14", OutputFormatter.format(FlowDataType.FlowDouble(3.14)))
    }

    @Test
    fun testFlowBoolean() {
        assertEquals("true", OutputFormatter.format(FlowDataType.FlowBoolean(true)))
        assertEquals("false", OutputFormatter.format(FlowDataType.FlowBoolean(false)))
    }

    @Test
    fun testFlowArrayString() {
        val result = OutputFormatter.format(FlowDataType.FlowArrayString(arrayOf("a", "b", "c")))
        assertEquals("a\nb\nc", result)
    }

    @Test
    fun testFlowArrayInteger() {
        val result = OutputFormatter.format(FlowDataType.FlowArrayInteger(arrayOf(1, 2, 3)))
        assertEquals("1\n2\n3", result)
    }

    @Test
    fun testFlowArrayDouble() {
        val result = OutputFormatter.format(FlowDataType.FlowArrayDouble(arrayOf(1.1, 2.2)))
        assertEquals("1.1\n2.2", result)
    }

    @Test
    fun testFlowArrayBoolean() {
        val result = OutputFormatter.format(FlowDataType.FlowArrayBoolean(arrayOf(true, false, true)))
        assertEquals("true\nfalse\ntrue", result)
    }

    @Test
    fun testFlowCritiqueResult() {
        val input = FlowDataType.FlowString("original")
        val result = OutputFormatter.format(
            FlowDataType.FlowCritiqueResult(success = true, feedback = "looks good", input = input)
        )
        assertEquals("success: true\nfeedback: looks good", result)
    }

    @Test
    fun testFlowCritiqueResultFailure() {
        val input = FlowDataType.FlowString("original")
        val result = OutputFormatter.format(
            FlowDataType.FlowCritiqueResult(success = false, feedback = "needs work", input = input)
        )
        assertEquals("success: false\nfeedback: needs work", result)
    }

    @Test
    fun testParallelExecutionResult() {
        val output = FlowDataType.FlowString("child output")
        val result = OutputFormatter.format(
            FlowDataType.ParallelExecutionResult(
                name = "worker_1",
                input = FlowDataType.FlowString("child input"),
                output = output
            )
        )
        assertEquals("[worker_1] child output", result)
    }

    @Test
    fun testParallelExecutionResultWithNestedCritique() {
        val nested = FlowDataType.FlowCritiqueResult(
            success = true,
            feedback = "ok",
            input = FlowDataType.FlowString("")
        )
        val result = OutputFormatter.format(
            FlowDataType.ParallelExecutionResult(name = "verifier", input = FlowDataType.FlowString(""), output = nested)
        )
        assertEquals("[verifier] success: true\nfeedback: ok", result)
    }

    @Test
    fun testSingleElementArray() {
        assertEquals("only", OutputFormatter.format(FlowDataType.FlowArrayString(arrayOf("only"))))
    }

    @Test
    fun testEmptyArray() {
        assertEquals("", OutputFormatter.format(FlowDataType.FlowArrayString(arrayOf())))
    }
}
