package ai.koog.agents.example.calculator

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for basic calculator operations")
class CalculatorTools : ToolSet {

    @Tool
    @LLMDescription("Adds two numbers")
    fun plus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a + b).toString()
    }

    @Tool
    @LLMDescription("Subtracts the second number from the first")
    fun minus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a - b).toString()
    }

    @Tool
    @LLMDescription("Divides the first number by the second")
    fun divide(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a / b).toString()
    }

    @Tool
    @LLMDescription("Multiplies two numbers")
    fun multiply(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a * b).toString()
    }
}
