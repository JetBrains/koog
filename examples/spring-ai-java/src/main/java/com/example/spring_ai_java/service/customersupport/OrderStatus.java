package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import kotlinx.serialization.Serializable;

@Serializable
@LLMDescription("Current status of an order")
public class OrderStatus {

    @LLMDescription("The customer order ID")
    private final String orderId;

    @LLMDescription("Current status of the order, e.g. Shipped, Processing")
    private final String status;

    @LLMDescription("Estimated time of arrival")
    private final String eta;

    public OrderStatus(String orderId, String status, String eta) {
        this.orderId = orderId;
        this.status = status;
        this.eta = eta;
    }

    public String getOrderId() { return orderId; }
    public String getStatus() { return status; }
    public String getEta() { return eta; }
}
