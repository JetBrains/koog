package ai.koog.integration.tests.agent;

import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.annotations.LLMDescription;

public class CalculatorTools implements ToolSet {

    @Tool
    @LLMDescription(description = "Adds two numbers together")
    public int add(
        @LLMDescription(description = "First number") int a,
        @LLMDescription(description = "Second number") int b
    ) {
        return a + b;
    }

    @Tool
    @LLMDescription(description = "Multiplies two numbers")
    public int multiply(
        @LLMDescription(description = "First number") int a,
        @LLMDescription(description = "Second number") int b
    ) {
        return a * b;
    }
}
