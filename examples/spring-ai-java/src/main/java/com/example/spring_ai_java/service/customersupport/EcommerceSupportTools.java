package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

@LLMDescription("Tools for order lookup, delivery changes, and refund policy checks.")
public class EcommerceSupportTools implements ToolSet {

    @Tool
    @LLMDescription("Get the current status of an order by order ID.")
    public OrderStatus getOrderStatus(
        @LLMDescription("Customer order ID") String orderId
    ) {
        return new OrderStatus(orderId, "Shipped", "2 days");
    }

    @Tool
    @LLMDescription("Change the delivery address of an order if it is still eligible.")
    public ChangeDeliveryAddressResponse changeDeliveryAddress(
        @LLMDescription("Customer order ID") String orderId,
        @LLMDescription("New delivery address") String newAddress
    ) {
        return new ChangeDeliveryAddressResponse(orderId, true, newAddress);
    }

    @Tool
    @LLMDescription("Check whether an order is eligible for refund or return.")
    public RefundEligibility checkRefundEligibility(
        @LLMDescription("Customer order ID") String orderId
    ) {
        return new RefundEligibility(orderId, true, "No reason provided");
    }

    @Tool
    @LLMDescription("Answer a general store policy question.")
    public String getPolicy(
        @LLMDescription("Policy topic, for example returns, refunds, shipping") PolicyTopic topic
    ) {
        return switch (topic) {
            case REFUND, RETURNS -> "Returns are accepted within 30 days for eligible items.";
            case SHIPPING -> "Standard shipping takes 3 to 5 business days.";
            case OTHER -> "No matching policy found.";
        };
    }
}
