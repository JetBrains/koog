package ai.grazie.code.agents.example.banking.routing

import ai.grazie.code.agents.core.dsl.extension.SerializableSubgraphResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@SerialName("UserRequestType")
@Serializable
enum class RequestType { Transfer, Analytics }

@Serializable
data class ClassifiedBankRequest(
    val requestType: RequestType,
    val userRequest: String
) : SerializableSubgraphResult<ClassifiedBankRequest> {
    override fun getSerializer() = serializer()
}