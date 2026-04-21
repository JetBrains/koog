package ai.koog.agents.example.calculator;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Java implementation of calculator tools, mirroring {@code CalculatorTools.kt}.
 *
 * <p>Demonstrates how to define agent tools using the {@code @Tool} and {@code @LLMDescription}
 * annotations on a class that implements {@link ToolSet}.
 *
 * <p>Note: {@code @LLMDescription} uses {@code value} as its annotation attribute.
 */
@LLMDescription("Tools for basic calculator operations")
public class CalculatorTools implements ToolSet {

    @Tool
    @LLMDescription("Adds two numbers")
    public String plus(
        @LLMDescription("First number") float a,
        @LLMDescription("Second number") float b
    ) {
        return String.valueOf(a + b);
    }

    @Tool
    @LLMDescription("Subtracts the second number from the first")
    public String minus(
        @LLMDescription("First number") float a,
        @LLMDescription("Second number") float b
    ) {
        return String.valueOf(a - b);
    }

    @Tool
    @LLMDescription("Divides the first number by the second")
    public String divide(
        @LLMDescription("First number") float a,
        @LLMDescription("Second number") float b
    ) {
        return String.valueOf(a / b);
    }

    @Tool
    @LLMDescription("Multiplies two numbers")
    public String multiply(
        @LLMDescription("First number") float a,
        @LLMDescription("Second number") float b
    ) {
        return String.valueOf(a * b);
    }
}
