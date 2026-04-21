package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.core.tools.annotations.LLMDescription;

@LLMDescription("The type of customer support request detected from the user message")
public enum SupportIntent {
    ORDER_STATUS,
    CHANGE_ADDRESS,
    REFUND,
    QUESTION,
    OTHER
}
