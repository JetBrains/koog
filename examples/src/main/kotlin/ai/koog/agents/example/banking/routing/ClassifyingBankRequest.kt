package ai.koog.agents.example.banking.routing

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.agent.SerializableSubgraphResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@SerialName("UserRequestType")
@Serializable
@LLMDescription("Type of user request: Transfer or Analytics")
enum class RequestType { Transfer, Analytics }

@Serializable
@LLMDescription("The bank request that was classified by the agent.")
data class ClassifiedBankRequest(
    @LLMDescription("Type of request: Transfer or Analytics")
    val requestType: RequestType,
    @LLMDescription("Actual request to be performed by the banking application")
    val userRequest: String
) : SerializableSubgraphResult<ClassifiedBankRequest> {
    override fun getSerializer() = serializer()
}
