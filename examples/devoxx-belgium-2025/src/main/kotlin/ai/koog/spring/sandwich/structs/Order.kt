package ai.koog.spring.sandwich.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
enum class ShippingMethod {
    DHL, DPD, HERMES, UBER, UNKNOWN
}

@Serializable
data class OrderInfo(
    val orderId: Int,
    val shippingMethod: ShippingMethod,
    val origin: String,
    val destination: String,
)