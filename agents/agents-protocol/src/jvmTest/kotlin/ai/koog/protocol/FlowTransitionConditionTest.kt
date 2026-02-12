package ai.koog.protocol

import ai.koog.protocol.agent.FlowDataType
import ai.koog.protocol.flow.ConditionOperationKind
import ai.koog.protocol.flow.KoogStrategyFactory
import ai.koog.protocol.transition.FlowTransitionCondition
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowTransitionConditionTest {

    private fun evaluateCondition(output: FlowDataType, condition: FlowTransitionCondition): Boolean {
        // Use reflection to access the private evaluateCondition method
        val method = KoogStrategyFactory::class.java.getDeclaredMethod(
            "evaluateCondition",
            FlowDataType::class.java,
            FlowTransitionCondition::class.java
        )
        method.isAccessible = true
        return method.invoke(KoogStrategyFactory, output, condition) as Boolean
    }

    //region EQUALS

    @Test
    fun testConditionEquals_withBoolean_true() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withBoolean_false() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withInt() {
        val output = FlowDataType.FlowInteger(42)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowInteger(42)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withInt_notEqual() {
        val output = FlowDataType.FlowInteger(42)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowInteger(100)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withString() {
        val output = FlowDataType.FlowString("hello")
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("hello")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withString_notEqual() {
        val output = FlowDataType.FlowString("hello")
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("world")
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withDouble() {
        val output = FlowDataType.FlowDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withCritiqueResult_success() {
        val output = FlowDataType.FlowCritiqueResult(
            success = true,
            feedback = "Great!",
            input = FlowDataType.FlowString("test")
        )
        val condition = FlowTransitionCondition(
            variable = "output.success",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withCritiqueResult_feedback() {
        val output = FlowDataType.FlowCritiqueResult(
            success = true,
            feedback = "Great!",
            input = FlowDataType.FlowString("test")
        )
        val condition = FlowTransitionCondition(
            variable = "output.feedback",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowString("Great!")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionEquals_withMixedNumericTypes_intAndDouble() {
        val output = FlowDataType.FlowInteger(42)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.EQUALS,
            value = FlowDataType.FlowDouble(42.0)
        )
        // Note: This compares 42 == 42.0 which returns false because they are different types,
        // For equality to work, both values must be the same type
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion EQUALS

    //region NOT_EQUALS

    @Test
    fun testConditionNotEquals_withBoolean_true() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowDataType.FlowBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withBoolean_false() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowDataType.FlowBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withInt() {
        val output = FlowDataType.FlowInteger(42)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowDataType.FlowInteger(100)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNotEquals_withString() {
        val output = FlowDataType.FlowString("hello")
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT_EQUALS,
            value = FlowDataType.FlowString("world")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion NOT_EQUALS

    //region MORE

    @Test
    fun testConditionMore_withInt_true() {
        val output = FlowDataType.FlowInteger(100)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowInteger(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withInt_false() {
        val output = FlowDataType.FlowInteger(25)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowInteger(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withInt_equal() {
        val output = FlowDataType.FlowInteger(50)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowInteger(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withDouble() {
        val output = FlowDataType.FlowDouble(5.0)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withString() {
        val output = FlowDataType.FlowString("banana")
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowString("apple")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMore_withMixedNumericTypes() {
        val output = FlowDataType.FlowInteger(100)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE,
            value = FlowDataType.FlowDouble(50.5)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion MORE

    //region LESS

    @Test
    fun testConditionLess_withInt_true() {
        val output = FlowDataType.FlowInteger(25)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS,
            value = FlowDataType.FlowInteger(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withInt_false() {
        val output = FlowDataType.FlowInteger(100)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS,
            value = FlowDataType.FlowInteger(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withInt_equal() {
        val output = FlowDataType.FlowInteger(50)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS,
            value = FlowDataType.FlowInteger(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withDouble() {
        val output = FlowDataType.FlowDouble(2.0)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS,
            value = FlowDataType.FlowDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLess_withString() {
        val output = FlowDataType.FlowString("apple")
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS,
            value = FlowDataType.FlowString("banana")
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion LESS

    //region MORE_OR_EQUAL

    @Test
    fun testConditionMoreOrEqual_withInt_more() {
        val output = FlowDataType.FlowInteger(100)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowDataType.FlowInteger(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withInt_equal() {
        val output = FlowDataType.FlowInteger(50)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowDataType.FlowInteger(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withInt_less() {
        val output = FlowDataType.FlowInteger(25)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowDataType.FlowInteger(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionMoreOrEqual_withDouble() {
        val output = FlowDataType.FlowDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.MORE_OR_EQUAL,
            value = FlowDataType.FlowDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion MORE_OR_EQUAL

    //region LESS_OR_EQUAL

    @Test
    fun testConditionLessOrEqual_withInt_less() {
        val output = FlowDataType.FlowInteger(25)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowDataType.FlowInteger(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withInt_equal() {
        val output = FlowDataType.FlowInteger(50)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowDataType.FlowInteger(50)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withInt_more() {
        val output = FlowDataType.FlowInteger(100)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowDataType.FlowInteger(50)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionLessOrEqual_withDouble() {
        val output = FlowDataType.FlowDouble(3.14)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.LESS_OR_EQUAL,
            value = FlowDataType.FlowDouble(3.14)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion LESS_OR_EQUAL

    //region NOT

    @Test
    fun testConditionNot_trueNotEqualsFalse() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT,
            value = FlowDataType.FlowBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNot_trueEqualsTrue() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT,
            value = FlowDataType.FlowBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionNot_falseNotEqualsTrue() {
        val output = FlowDataType.FlowBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.NOT,
            value = FlowDataType.FlowBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    //endregion NOT

    //region AND

    @Test
    fun testConditionAnd_trueAndTrue() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.AND,
            value = FlowDataType.FlowBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_trueAndFalse() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.AND,
            value = FlowDataType.FlowBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_falseAndTrue() {
        val output = FlowDataType.FlowBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.AND,
            value = FlowDataType.FlowBoolean(true)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionAnd_falseAndFalse() {
        val output = FlowDataType.FlowBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.AND,
            value = FlowDataType.FlowBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion AND

    //region OR

    @Test
    fun testConditionOr_trueOrTrue() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.OR,
            value = FlowDataType.FlowBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_trueOrFalse() {
        val output = FlowDataType.FlowBoolean(true)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.OR,
            value = FlowDataType.FlowBoolean(false)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_falseOrTrue() {
        val output = FlowDataType.FlowBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.OR,
            value = FlowDataType.FlowBoolean(true)
        )
        assertTrue(evaluateCondition(output, condition))
    }

    @Test
    fun testConditionOr_falseOrFalse() {
        val output = FlowDataType.FlowBoolean(false)
        val condition = FlowTransitionCondition(
            variable = "output.data",
            operation = ConditionOperationKind.OR,
            value = FlowDataType.FlowBoolean(false)
        )
        assertFalse(evaluateCondition(output, condition))
    }

    //endregion OR
}
