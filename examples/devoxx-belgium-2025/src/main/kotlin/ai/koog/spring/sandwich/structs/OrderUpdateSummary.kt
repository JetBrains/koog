package ai.koog.spring.sandwich.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
@LLMDescription("Summary about what has been updated")
data class OrderUpdateSummary(
    val orderId: Int,
    @LLMDescription("Brief summary of the changes")
    val changes: String
)