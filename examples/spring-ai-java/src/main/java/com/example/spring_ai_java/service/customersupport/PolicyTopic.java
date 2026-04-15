package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;

@LLMDescription("Policy topic for customer support queries")
public enum PolicyTopic {
    RETURNS,
    REFUND,
    SHIPPING,
    OTHER
}
