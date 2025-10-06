package ai.koog.spring.sandwich.structs

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
@LLMDescription("User account information including balance and active orders")
data class UserAccount(
    @LLMDescription("Unique identifier of the user")
    val userId: String,
    @LLMDescription("Current account balance in cents")
    val balanceCents: Int,
    @LLMDescription("IDs of active orders for the user")
    val activeOrderIds: List<Int>,
)