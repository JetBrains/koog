package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@LLMDescription("Summary about what has been done to resolve the account issue")
public record AccountIssueSolution(
        @JsonProperty("accountNumber")
        @LLMDescription("Account number that was affected")
        String accountNumber,

        @JsonProperty("actionsTaken")
        @LLMDescription("Brief summary of the actions taken to resolve the issue")
        String actionsTaken
) {
    @JsonCreator
    public AccountIssueSolution {}

    public static AccountIssueSolution empty() {
        return new AccountIssueSolution("account-123", "Nothing changed");
    }
}
