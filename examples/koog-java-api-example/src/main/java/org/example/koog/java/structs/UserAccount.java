package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;

import java.util.List;
import java.util.Objects;

@LLMDescription(description = "User account information including balance and active orders")
public class UserAccount {
    @LLMDescription(description = "Unique identifier of the user")
    private final String userId;

    @LLMDescription(description = "Current account balance in cents")
    private final int balanceCents;

    @LLMDescription(description = "IDs of active orders for the user")
    private final List<Integer> activeOrderIds;

    public UserAccount(String userId, int balanceCents, List<Integer> activeOrderIds) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.balanceCents = balanceCents;
        this.activeOrderIds = Objects.requireNonNull(activeOrderIds, "activeOrderIds");
    }

    public String getUserId() { return userId; }
    public int getBalanceCents() { return balanceCents; }
    public List<Integer> getActiveOrderIds() { return activeOrderIds; }
}
