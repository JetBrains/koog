package org.example.koog.spring.java.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import org.example.koog.spring.java.structs.Transaction;
import org.example.koog.spring.java.structs.UserAccountInfo;

import java.time.Instant;
import java.util.List;

public class AccountReadUtils implements ToolSet {
    private final String userId;

    public AccountReadUtils(String userId) {
        this.userId = userId;
    }

    @Tool
    @LLMDescription("Returns a list of transactions for the current user")
    public List<Transaction> getLatestTransactions(
            Instant startDate,
            @LLMDescription("Optional transaction status filter (if null -- all statuses will be returned)")
            Transaction.Status status
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
