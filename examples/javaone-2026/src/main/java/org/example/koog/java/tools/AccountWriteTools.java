package org.example.koog.java.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import org.example.koog.java.structs.Transaction;
import org.example.koog.java.structs.TransferResult;

public class AccountWriteTools implements ToolSet {

    private final String userId;
    public AccountWriteTools(String userId) {
        this.userId = userId;
    }

    @Tool
    @LLMDescription("Initiates a dispute for the transaction, returns disputeID")
    public String initiateDispute(String transactionId) {
        return "dispute-123";
    }

    @Tool
    @LLMDescription("Cancels the specified transaction")
    public Transaction.Status cancelTransaction(String transactionId) {
        return Transaction.Status.CANCELED;
    }

    @Tool
    @LLMDescription("Transfers money to the recipient")
    public TransferResult transferMoney(
            @LLMDescription("ID of the recipient") String recipientId,
            @LLMDescription("Amount in USD to be transfered") Integer amount
    ) {
        return new TransferResult(false, "Recipient with this ID wasn't found", "transfer-123");
    }
}
