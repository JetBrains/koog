package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;

@LLMDescription(description = "Shipping method for an order")
public enum ShippingMethod {
    DHL, DPD, HERMES, UBER, UNKNOWN
}
