package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;

/**
 * Normalized support request extracted from a user message.
 * Used as a structured output type for the intent classification sub-task.
 * Jackson deserializes the LLM JSON response into this class.
 */
@LLMDescription("Normalized support request extracted from a user message.")
public class SupportRequest {

    @LLMDescription("Detected support intent")
    private SupportIntent intent;

    @LLMDescription("Order ID if present, otherwise null")
    private String orderId;

    @LLMDescription("New address if the user wants to change delivery address")
    private String newAddress;

    @LLMDescription("Original user request rewritten clearly for the downstream handler")
    private String userRequest;

    /** No-arg constructor required by Jackson for deserialization. */
    public SupportRequest() {}

    public SupportIntent getIntent() { return intent; }
    public void setIntent(SupportIntent intent) { this.intent = intent; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getNewAddress() { return newAddress; }
    public void setNewAddress(String newAddress) { this.newAddress = newAddress; }

    public String getUserRequest() { return userRequest; }
    public void setUserRequest(String userRequest) { this.userRequest = userRequest; }
}
