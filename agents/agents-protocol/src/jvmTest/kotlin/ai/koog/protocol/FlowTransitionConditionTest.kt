package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentInput
import ai.koog.protocol.agent.InputBoolean
import ai.koog.protocol.agent.InputCritiqueResult
import ai.koog.protocol.agent.InputDouble
import ai.koog.protocol.agent.InputInt
import ai.koog.protocol.agent.InputString
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.flow.KoogStrategyFactory
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowTransitionConditionTest {

    private fun evaluateCondition(output: FlowAgentInput, condition: FlowTransitionCondition): Boolean {
        // Use reflection to access the private evaluateCondition method
        val method = KoogStrategyFactory::class.java.getDeclaredMethod(
            "evaluateCondition",
            FlowAgentInput::class.java,
            FlowTransitionCondition::class.java
        )
        method.isAccessible = true
        return method.invoke(KoogStrategyFactory, output, condition) as Boolean
    }

    //region EQUALS

    @Test
    fun testConditionEquals_withBoolean_true() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withBoolean_false() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withInt() {
        val output = InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputInt(42)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withInt_notEqual() {
        val output = InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputInt(100)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withString() {
        val output = InputString("hello")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputString("hello")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withString_notEqual() {
        val output = InputString("hello")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputString("world")
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withDouble() {
        val output = InputDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withCritiqueResult_success() {
        val output = InputCritiqueResult(
            success = true,
            feedback = "Great!",
            input = InputString("test")
        )
        val condition = FlowTransitionCondition(
            variable = "input.success",
            operation = ConditionOperationKind.EQUALS,
            value = InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withCritiqueResult_feedback() {
        val output = InputCritiqueResult(
            success = true,
            feedback = "Great!",
            input = InputString("test")
        )
        val condition = FlowTransitionCondition(
            variable = "input.feedback",
            operation = ConditionOperationKind.EQUALS,
            value = InputString("Great!")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withMixedNumericTypes_intAndDouble() {
        val output = InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = InputDouble(42.0)
        )
        // Note: This compares 42 == 42.0 which returns false because they are different types
        // For equality to work, both values must be the same type
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion EQUALS

    //region NOT_EQUALS

    @Test
    fun testConditionNotEquals_withBoolean_true() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = InputBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withBoolean_false() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = InputBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withInt() {
        val output = InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = InputInt(100)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withString() {
        val output = InputString("hello")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = InputString("world")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion NOT_EQUALS

    //region MORE

    @Test
    fun testConditionMore_withInt_true() {
        val output = InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withInt_false() {
        val output = InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withInt_equal() {
        val output = InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withDouble() {
        val output = InputDouble(5.0)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withString() {
        val output = InputString("banana")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = InputString("apple")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withMixedNumericTypes() {
        val output = InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = InputDouble(50.5)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion MORE

    //region LESS

    @Test
    fun testConditionLess_withInt_true() {
        val output = InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withInt_false() {
        val output = InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withInt_equal() {
        val output = InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withDouble() {
        val output = InputDouble(2.0)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withString() {
        val output = InputString("apple")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = InputString("banana")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion LESS

    //region MORE_OR_EQUAL

    @Test
    fun testConditionMoreOrEqual_withInt_more() {
        val output = InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withInt_equal() {
        val output = InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withInt_less() {
        val output = InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withDouble() {
        val output = InputDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion MORE_OR_EQUAL

    //region LESS_OR_EQUAL

    @Test
    fun testConditionLessOrEqual_withInt_less() {
        val output = InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withInt_equal() {
        val output = InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withInt_more() {
        val output = InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withDouble() {
        val output = InputDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion LESS_OR_EQUAL

    //region NOT

    @Test
    fun testConditionNot_trueNotEqualsFalse() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT,
            value = InputBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNot_trueEqualsTrue() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT,
            value = InputBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNot_falseNotEqualsTrue() {
        val output = InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT,
            value = InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion NOT

    //region AND

    @Test
    fun testConditionAnd_trueAndTrue() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_trueAndFalse() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_falseAndTrue() {
        val output = InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = InputBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_falseAndFalse() {
        val output = InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion AND

    //region OR

    @Test
    fun testConditionOr_trueOrTrue() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_trueOrFalse() {
        val output = InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = InputBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_falseOrTrue() {
        val output = InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_falseOrFalse() {
        val output = InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion OR
}
