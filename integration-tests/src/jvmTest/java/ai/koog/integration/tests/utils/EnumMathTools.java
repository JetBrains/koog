package ai.koog.integration.tests.utils;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public final class EnumMathTools implements ToolSet {
    public enum OperationType {
        ADD,
        SUBTRACT
    }

    @Tool
    @LLMDescription("Executes math operation over two integers and returns the result")
    public int applyOperation(
        @LLMDescription("Operation type: ADD or SUBTRACT") OperationType operation,
        @LLMDescription("First operand") int left,
        @LLMDescription("Second operand") int right
    ) {
        return operation == OperationType.ADD ? left + right : left - right;
    }
}
