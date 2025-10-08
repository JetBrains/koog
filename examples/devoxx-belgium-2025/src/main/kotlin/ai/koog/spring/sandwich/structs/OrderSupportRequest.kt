package ai.koog.spring.sandwich.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
@LLMDescription("Full information about the user's issue with the order")
data class OrderSupportRequest(
    @LLMDescription("ID of the order in the database")
    val orderId: Int,
    @LLMDescription("Chosen shipment method for the order")
    val shippingMethod: ShippingMethod,
    @LLMDescription("Address of the origin")
    val originAddress: String,
    @LLMDescription("Address where the order must be delivered")
    val destinationAddress: String,
    @LLMDescription("Price of the order in US dollars")
    val price: Int,
    @LLMDescription("What exactly is the user's issue with the order")
    val problem: String,
    @LLMDescription("Was the issue already resolved?")
    val resolved: Boolean
) {
    fun emptyUpdate(): OrderUpdateSummary = OrderUpdateSummary(orderId, "Nothing changed")
}