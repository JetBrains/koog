package ai.koog.integration.tests.utils;

import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.annotations.LLMDescription;

import java.util.Random;

public class NumberTools implements ToolSet {

    private final Random random = new Random();

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

    @Tool
    @LLMDescription("Generates a random number between 0 and 999. You must use this tool to get random numbers, you cannot generate them yourself.")
    public int generateRandomNumber() {
        return random.nextInt(1000);
    }
}
