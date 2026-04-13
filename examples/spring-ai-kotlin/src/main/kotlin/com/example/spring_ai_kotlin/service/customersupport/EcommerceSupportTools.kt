package com.example.spring_ai_kotlin.service.customersupport

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@LLMDescription("Tools for order lookup, delivery changes, and refund policy checks.")
class EcommerceSupportTools : ToolSet {

    @Tool
    @LLMDescription("Get the current status of an order by order ID.")
    fun getOrderStatus(
        @LLMDescription("Customer order ID") orderId: String
    ): String {
        return """{"orderId":"$orderId","status":"In transit","eta":"Tomorrow"}"""
    }

    @Tool
    @LLMDescription("Change the delivery address of an order if it is still eligible.")
    fun changeDeliveryAddress(
        @LLMDescription("Customer order ID") orderId: String,
        @LLMDescription("New delivery address") newAddress: String
    ): String {
        return """{"orderId":"$orderId","updated":true,"newAddress":"$newAddress"}"""
    }

    @Tool
    @LLMDescription("Check whether an order is eligible for refund or return.")
    fun checkRefundEligibility(
        @LLMDescription("Customer order ID") orderId: String
    ): String {
        return """{"orderId":"$orderId","eligible":true,"window":"30 days"}"""
    }

    @Tool
    @LLMDescription("Answer a general store policy question.")
    fun getPolicy(
        @LLMDescription("Policy topic, for example returns, refunds, shipping")
        topic: String
    ): String {
        return when (topic.lowercase()) {
            "returns", "refunds" -> "Returns are accepted within 30 days for eligible items."
            "shipping" -> "Standard shipping takes 3 to 5 business days."
            else -> "No matching policy found."
        }
    }
}

class EcommerceSupportRollbackTools : ToolSet {
    @Tool
    @LLMDescription("Change the delivery address for an order if the order is still eligible.")
    fun changeDeliveryAddressToHome(
        @LLMDescription("The customer order ID") orderId: String,
        @LLMDescription("The new delivery address") newAddress: String
    ): String {
        // Replace with a real API call
        return """{"orderId":"$orderId","updated":true,"newAddress":"$newAddress"}"""
    }
}
