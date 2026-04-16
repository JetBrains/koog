package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import kotlinx.serialization.Serializable;

@Serializable
@LLMDescription("Result of a delivery address change request")
public class ChangeDeliveryAddressResponse {

    @LLMDescription("The customer order ID")
    private final String orderId;

    @LLMDescription("Whether the address was successfully updated")
    private final boolean updated;

    @LLMDescription("The new delivery address")
    private final String newAddress;

    public ChangeDeliveryAddressResponse(String orderId, boolean updated, String newAddress) {
        this.orderId = orderId;
        this.updated = updated;
        this.newAddress = newAddress;
    }

    public String getOrderId() { return orderId; }
    public boolean isUpdated() { return updated; }
    public String getNewAddress() { return newAddress; }
}
