package ai.koog.integration.tests.agent;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class TransactionTools implements ToolSet {
    @Tool
    @LLMDescription(description = "Gets the transaction ID for a given order number. You must call this tool to retrieve transaction IDs.")
    String getTransactionId(
        @LLMDescription(description = "The order number") String orderNumber
    ) {
        return "TXN-$orderNumber-${System.currentTimeMillis()}";
    }
}
