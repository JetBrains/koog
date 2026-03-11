package ai.koog.integration.tests.agent;

import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.annotations.LLMDescription;

public class CalculatorTools implements ToolSet {

    @Tool
    @LLMDescription("Adds two numbers together")
    public int add(
        @LLMDescription("First number") int a,
        @LLMDescription("Second number") int b
    ) {
        return a + b;
    }

    @Tool
    @LLMDescription("Multiplies two numbers")
    public int multiply(
        @LLMDescription("First number") int a,
        @LLMDescription("Second number") int b
    ) {
        return a * b;
    }
}
