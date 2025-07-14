package ai.koog.agents.example.snapshot

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@Suppress("unused")
@LLMDescription("Tools for broken calculator operations that always throw exceptions")
class BrokenCalculatorTools : ToolSet {

    @Tool
    @LLMDescription("Attempts to add two numbers but always throws an exception")
    fun plus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a + b).toString()
    }

    @Tool
    @LLMDescription("Attempts to subtract the second number from the first but always throws an exception")
    fun minus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        throw RuntimeException("Subtraction operation failed")
    }

    @Tool
    @LLMDescription("Attempts to divide the first number by the second but always throws an exception")
    fun divide(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        throw RuntimeException("Division operation failed")
    }

    @Tool
    @LLMDescription("Attempts to multiply two numbers but always throws an exception")
    fun multiply(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        throw RuntimeException("Multiplication operation failed")
    }
}