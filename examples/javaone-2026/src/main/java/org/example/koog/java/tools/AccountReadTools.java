package org.example.koog.java.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import org.example.koog.java.structs.Transaction;
import org.example.koog.java.structs.UserAccountInfo;

import java.time.Instant;
import java.util.List;

public class AccountReadTools implements ToolSet {
    private final String userId;

    public AccountReadTools(String userId) {
        this.userId = userId;
    }

    @Tool
    @LLMDescription("Returns a list of transactions for the current user")
    public List<Transaction> getLatestTransactions(
            Instant startDate,
            Transaction.Status status // ? = null =>>> all statuses
    ) {
        return List.of();
    }

    @Tool
    @LLMDescription("Get account balance (in USD) for the current user")
    public Integer getAccountBalance() {
        return 1000000;
    }

    @Tool
    @LLMDescription("Get account information for the current user: name, credentials, balance, etc.")
    public UserAccountInfo readAccountInfo() {
        return new UserAccountInfo(
                "",
                "",
                "",
                "",
                "",
                "",
                100500,
                Instant.now(),
                Instant.now()
        );
    }
}
