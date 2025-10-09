package ai.koog.spring.sandwich.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
@LLMDescription("Basic representation of an item in an order")
data class Item(
    @LLMDescription("Stock keeping unit or product identifier")
    val sku: String,
    @LLMDescription("Human readable name")
    val name: String,
    @LLMDescription("Quantity of the item")
    val quantity: Int,
    @LLMDescription("Price per single unit in cents")
    val priceCents: Int,
)