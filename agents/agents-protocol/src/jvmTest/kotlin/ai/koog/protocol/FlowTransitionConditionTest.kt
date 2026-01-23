package ai.koog.protocol

import ai.koog.protocol.agent.FlowAgentInput
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
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withBoolean_false() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withInt() {
        val output = FlowAgentInput.InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputInt(42)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withInt_notEqual() {
        val output = FlowAgentInput.InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputInt(100)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withString() {
        val output = FlowAgentInput.InputString("hello")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputString("hello")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withString_notEqual() {
        val output = FlowAgentInput.InputString("hello")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputString("world")
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withDouble() {
        val output = FlowAgentInput.InputDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withCritiqueResult_success() {
        val output = FlowAgentInput.InputCritiqueResult(
            success = true,
            feedback = "Great!",
            input = FlowAgentInput.InputString("test")
        )
        val condition = FlowTransitionCondition(
            variable = "input.success",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withCritiqueResult_feedback() {
        val output = FlowAgentInput.InputCritiqueResult(
            success = true,
            feedback = "Great!",
            input = FlowAgentInput.InputString("test")
        )
        val condition = FlowTransitionCondition(
            variable = "input.feedback",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputString("Great!")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withMixedNumericTypes_intAndDouble() {
        val output = FlowAgentInput.InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowAgentInput.InputDouble(42.0)
        )
        // Note: This compares 42 == 42.0 which returns false because they are different types
        // For equality to work, both values must be the same type
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion EQUALS

    //region NOT_EQUALS

    @Test
    fun testConditionNotEquals_withBoolean_true() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withBoolean_false() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withInt() {
        val output = FlowAgentInput.InputInt(42)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowAgentInput.InputInt(100)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withString() {
        val output = FlowAgentInput.InputString("hello")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowAgentInput.InputString("world")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion NOT_EQUALS

    //region MORE

    @Test
    fun testConditionMore_withInt_true() {
        val output = FlowAgentInput.InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = FlowAgentInput.InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withInt_false() {
        val output = FlowAgentInput.InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = FlowAgentInput.InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withInt_equal() {
        val output = FlowAgentInput.InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = FlowAgentInput.InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withDouble() {
        val output = FlowAgentInput.InputDouble(5.0)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = FlowAgentInput.InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withString() {
        val output = FlowAgentInput.InputString("banana")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = FlowAgentInput.InputString("apple")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withMixedNumericTypes() {
        val output = FlowAgentInput.InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE,
            value = FlowAgentInput.InputDouble(50.5)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion MORE

    //region LESS

    @Test
    fun testConditionLess_withInt_true() {
        val output = FlowAgentInput.InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = FlowAgentInput.InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withInt_false() {
        val output = FlowAgentInput.InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = FlowAgentInput.InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withInt_equal() {
        val output = FlowAgentInput.InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = FlowAgentInput.InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withDouble() {
        val output = FlowAgentInput.InputDouble(2.0)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = FlowAgentInput.InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withString() {
        val output = FlowAgentInput.InputString("apple")
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS,
            value = FlowAgentInput.InputString("banana")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion LESS

    //region MORE_OR_EQUAL

    @Test
    fun testConditionMoreOrEqual_withInt_more() {
        val output = FlowAgentInput.InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowAgentInput.InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withInt_equal() {
        val output = FlowAgentInput.InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowAgentInput.InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withInt_less() {
        val output = FlowAgentInput.InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowAgentInput.InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withDouble() {
        val output = FlowAgentInput.InputDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowAgentInput.InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion MORE_OR_EQUAL

    //region LESS_OR_EQUAL

    @Test
    fun testConditionLessOrEqual_withInt_less() {
        val output = FlowAgentInput.InputInt(25)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowAgentInput.InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withInt_equal() {
        val output = FlowAgentInput.InputInt(50)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowAgentInput.InputInt(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withInt_more() {
        val output = FlowAgentInput.InputInt(100)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowAgentInput.InputInt(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withDouble() {
        val output = FlowAgentInput.InputDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowAgentInput.InputDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion LESS_OR_EQUAL

    //region NOT

    @Test
    fun testConditionNot_trueNotEqualsFalse() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNot_trueEqualsTrue() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNot_falseNotEqualsTrue() {
        val output = FlowAgentInput.InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.NOT,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion NOT

    //region AND

    @Test
    fun testConditionAnd_trueAndTrue() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_trueAndFalse() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_falseAndTrue() {
        val output = FlowAgentInput.InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_falseAndFalse() {
        val output = FlowAgentInput.InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.AND,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion AND

    //region OR

    @Test
    fun testConditionOr_trueOrTrue() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_trueOrFalse() {
        val output = FlowAgentInput.InputBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_falseOrTrue() {
        val output = FlowAgentInput.InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = FlowAgentInput.InputBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_falseOrFalse() {
        val output = FlowAgentInput.InputBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "input.data",
            operation = ConditionOperationKind.OR,
            value = FlowAgentInput.InputBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion OR
}
