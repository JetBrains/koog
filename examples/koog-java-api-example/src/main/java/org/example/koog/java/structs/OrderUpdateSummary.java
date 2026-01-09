package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import kotlinx.serialization.Serializable;

@Serializable
@LLMDescription(description = "Summary about what has been updated")
public class OrderUpdateSummary {
    private final Integer orderId;

    @LLMDescription(description = "Brief summary of the changes")
    private final String changes;

    public OrderUpdateSummary(Integer orderId, String changes) {
        this.orderId = orderId;
        this.changes = changes;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public String getChanges() {
        return changes;
    }

    public static OrderUpdateSummary empty() {
        return new OrderUpdateSummary(null, "Nothing changed");
    }
}