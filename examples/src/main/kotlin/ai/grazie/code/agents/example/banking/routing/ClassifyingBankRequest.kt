package ai.grazie.code.agents.example.banking.routing

import ai.grazie.code.agents.core.dsl.extension.ProvideSubgraphResult
import ai.grazie.code.agents.core.dsl.extension.SubgraphResult
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterDescriptor
import ai.grazie.code.agents.core.tools.ToolParameterType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Suppress("unused")
@SerialName("UserRequestType")
@Serializable
enum class RequestType { Transfer, Analytics }

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = true
}

@Serializable
data class ClassifiedBankRequest(
    val requestType: RequestType,
    val userRequest: String
) : SubgraphResult {
    override fun toStringDefault(): String = json.encodeToString(serializer(), this)
}

object ProvideClassifiedRequest : ProvideSubgraphResult<ClassifiedBankRequest>() {
    override val argsSerializer = ClassifiedBankRequest.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "provide_classified_request",
        description = "Provide a classified bank request",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "requestType",
                description = "type of banking action",
                type = ToolParameterType.Enum(RequestType.entries)
            ),
            ToolParameterDescriptor(
                name = "userRequest",
                description = "Initial user's request",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun execute(args: ClassifiedBankRequest): ClassifiedBankRequest {
        return args
    }
}
