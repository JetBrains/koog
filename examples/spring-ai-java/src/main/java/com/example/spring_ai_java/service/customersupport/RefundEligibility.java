package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import kotlinx.serialization.Serializable;

@Serializable
@LLMDescription("Refund eligibility status for an order")
public class RefundEligibility {

    @LLMDescription("The customer order ID")
    private final String orderId;

    @LLMDescription("Whether the order is eligible for a refund")
    private final boolean eligible;

    @LLMDescription("Reason for the eligibility decision")
    private final String reason;

    public RefundEligibility(String orderId, boolean eligible, String reason) {
        this.orderId = orderId;
        this.eligible = eligible;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public boolean isEligible() { return eligible; }
    public String getReason() { return reason; }
}
