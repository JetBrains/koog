package ai.koog.spring.sandwich.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.spring.sandwich.structs.ShippingMethod
import ai.koog.spring.sandwich.tools.utils.InMemoryStore
import kotlinx.coroutines.delay
import kotlin.random.Random

object OrderTools: ToolSet {
    @Tool
    @LLMDescription("Contact the shipping carrier about an order and return their textual reply")
    suspend fun contactCarrier(
        @LLMDescription("Carrier used for the shipment")
        shippingMethod: ShippingMethod,
        @LLMDescription("Order identifier")
        orderId: Int,
        @LLMDescription("Question or request to send to the carrier")
        request: String
    ): String {
        // Simulate contacting a carrier API
        delay(100)
        val outcomes = listOf(
            "Carrier ${shippingMethod.name} acknowledges update for order #$orderId.",
            "Carrier ${shippingMethod.name} reports delay due to weather for order #$orderId.",
            "Carrier ${shippingMethod.name} updated address for order #$orderId.",
            "Carrier ${shippingMethod.name} could not find order #$orderId; please retry.",
        )
        return outcomes[Random.nextInt(outcomes.size)] + " Request='${request.take(120)}'"
    }

    @Tool
    @LLMDescription("Update the delivery address for the specified order")
    suspend fun updateAddress(
        @LLMDescription("Order identifier")
        orderId: Int,
        @LLMDescription("New delivery address")
        address: String
    ) {
        delay(50)
        val found = InMemoryStore.findOrder(orderId)
        if (found != null) {
            val (userId, idx) = found
            val list = InMemoryStore.ordersByUser[userId] ?: return
            val current = list[idx]
            list[idx] = current.copy(destination = address)
        }
    }
}