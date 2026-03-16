package ai.koog.agents.example.calculator.java;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Java implementation of calculator tools, mirroring {@code CalculatorTools.kt}.
 *
 * <p>Demonstrates how to define agent tools using the {@code @Tool} and {@code @LLMDescription}
 * annotations on a class that implements {@link ToolSet}.
 *
 * <p>Note: {@code @LLMDescription} uses {@code description} as its annotation attribute name,
 * so Java callers must use the named form: {@code @LLMDescription("...")}.
 */
@LLMDescription(description = "Tools for basic calculator operations")
public class CalculatorTools implements ToolSet {

    @Tool
    @LLMDescription(description = "Adds two numbers")
    public String plus(
        @LLMDescription(description = "First number") float a,
        @LLMDescription(description = "Second number") float b
    ) {
        return String.valueOf(a + b);
    }

    @Tool
    @LLMDescription(description = "Subtracts the second number from the first")
    public String minus(
        @LLMDescription(description = "First number") float a,
        @LLMDescription(description = "Second number") float b
    ) {
        return String.valueOf(a - b);
    }

    @Tool
    @LLMDescription(description = "Divides the first number by the second")
    public String divide(
        @LLMDescription(description = "First number") float a,
        @LLMDescription(description = "Second number") float b
    ) {
        return String.valueOf(a / b);
    }

    @Tool
    @LLMDescription(description = "Multiplies two numbers")
    public String multiply(
        @LLMDescription(description = "First number") float a,
        @LLMDescription(description = "Second number") float b
    ) {
        return String.valueOf(a * b);
    }
}
