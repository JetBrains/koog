package org.example.koog.java.structs;

import ai.koog.agents.core.tools.annotations.LLMDescription;

@LLMDescription("Shipping method for an order")
public enum ShippingMethod {
    DHL, DPD, HERMES, UBER, UNKNOWN
}
