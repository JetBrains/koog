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
    @LLMDescription(description = "Executes math operation over two integers and returns the result")
    public int applyOperation(
        @LLMDescription(description = "Operation type: ADD or SUBTRACT") OperationType operation,
        @LLMDescription(description = "First operand") int left,
        @LLMDescription(description = "Second operand") int right
    ) {
        return operation == OperationType.ADD ? left + right : left - right;
    }
}
