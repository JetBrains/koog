package org.example.koog.spring.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@LLMDescription("Full information about the user's issue with their bank account")
public record AccountIssueSummary(
        @JsonProperty("accountNumber")
        @LLMDescription("Account number of the user in the database")
        String accountNumber,

        @JsonProperty("username")
        @LLMDescription("Username of the account holder")
        String username,

        @JsonProperty("currentBalance")
        @LLMDescription("Current account balance in US dollars")
        Integer currentBalance,

        @JsonProperty("relatedTransactionId")
        @LLMDescription("ID of the transaction related to this issue, if applicable")
        String relatedTransactionId,

        @JsonProperty("disputeId")
        @LLMDescription("ID of the dispute if one was initiated for this issue")
        String disputeId,

        @JsonProperty("problem")
        @LLMDescription("What exactly is the user's issue with their account or transaction")
        String problem,

        @JsonProperty("resolved")
        @LLMDescription("Was the issue already resolved?")
        boolean resolved
) {
    @JsonCreator
    public AccountIssueSummary {
    }

    public static AccountIssueSummary some() {
        return new AccountIssueSummary("some-id", "Unknown", 0, null, null, "No issue", false);
    }
}
