package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class EcommerceSupportRollbackTools implements ToolSet {

    @Tool
    @LLMDescription("Change the delivery address for an order if the order is still eligible.")
    public ChangeDeliveryAddressResponse changeDeliveryAddressToHome(
        @LLMDescription("The customer order ID") String orderId,
        @LLMDescription("The new delivery address") String newAddress
    ) {
        // Replace with a real API call
        return new ChangeDeliveryAddressResponse(orderId, true, newAddress);
    }
}
