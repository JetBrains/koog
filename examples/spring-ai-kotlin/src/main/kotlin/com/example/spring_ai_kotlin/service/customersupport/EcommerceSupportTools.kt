package com.example.spring_ai_kotlin.service.customersupport

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.serialization.Serializable

@Serializable
data class OrderStatus(val orderId: String, val status: String, val eta: String)

@Serializable
data class ChangeDeliveryAddressResponse(val orderId: String, val updated: Boolean, val newAddress: String)

@Serializable
data class RefundEligibility(val orderId: String, val eligible: Boolean, val reason: String)

@Serializable
enum class PolicyTopic {
    RETURNS,
    REFUND,
    SHIPPING,
    OTHER
}

@LLMDescription("Tools for order lookup, delivery changes, and refund policy checks.")
class EcommerceSupportTools : ToolSet {

    @Tool
    @LLMDescription("Get the current status of an order by order ID.")
    fun getOrderStatus(
        @LLMDescription("Customer order ID") orderId: String
    ): OrderStatus {
        return OrderStatus(orderId = orderId, status = "Shipped", eta = "2 days")
    }

    @Tool
    @LLMDescription("Change the delivery address of an order if it is still eligible.")
    fun changeDeliveryAddress(
        @LLMDescription("Customer order ID") orderId: String,
        @LLMDescription("New delivery address") newAddress: String
    ): ChangeDeliveryAddressResponse {
        return ChangeDeliveryAddressResponse(orderId = orderId, updated = true, newAddress = newAddress)
    }

    @Tool
    @LLMDescription("Check whether an order is eligible for refund or return.")
    fun checkRefundEligibility(
        @LLMDescription("Customer order ID") orderId: String
    ): RefundEligibility {
        return RefundEligibility(orderId = orderId, eligible = true, reason = "No reason provided")
    }

    @Tool
    @LLMDescription("Answer a general store policy question.")
    fun getPolicy(
        @LLMDescription("Policy topic, for example returns, refunds, shipping")
        topic: PolicyTopic
    ): String {
        return when (topic) {
            PolicyTopic.REFUND, PolicyTopic.RETURNS -> "Returns are accepted within 30 days for eligible items."
            PolicyTopic.SHIPPING -> "Standard shipping takes 3 to 5 business days."
            PolicyTopic.OTHER -> "No matching policy found."
        }
    }
}

class EcommerceSupportRollbackTools : ToolSet {
    @Tool
    @LLMDescription("Change the delivery address for an order if the order is still eligible.")
    fun changeDeliveryAddressToHome(
        @LLMDescription("The customer order ID") orderId: String,
        @LLMDescription("The new delivery address") newAddress: String
    ): ChangeDeliveryAddressResponse {
        // Replace with a real API call
        return ChangeDeliveryAddressResponse(orderId = orderId, updated = true, newAddress = newAddress)
    }
}
