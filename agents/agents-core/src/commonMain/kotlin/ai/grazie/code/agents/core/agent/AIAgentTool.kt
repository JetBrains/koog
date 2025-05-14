package ai.grazie.code.agents.core.agent

import ai.grazie.code.agents.core.agent.AIAgentTool.AIAgentToolArgs
import ai.grazie.code.agents.core.agent.AIAgentTool.AIAgentToolResult
import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun AIAgent.asTool(
    agentDescription: String,
    name: String? = null,
    requestDescription: String = "Input for the task"
) = AIAgentTool(
    agent = this,
    agentName = name ?: this::class.simpleName!!.lowercase(),
    requestDescription = requestDescription,
    agentDescription = agentDescription
)


class AIAgentTool(
    val agent: AIAgent,
    agentName: String,
    agentDescription: String,
    requestDescription: String = "Input for the task"
) : Tool<AIAgentToolArgs, AIAgentToolResult>() {
    @Serializable
    data class AIAgentToolArgs(val request: String) : Args

    @Serializable
    data class AIAgentToolResult(
        val successful: Boolean,
        val errorMessage: String? = null,
        val result: String? = null
    ) : ToolResult {
        override fun toStringDefault(): String = Json.encodeToString(serializer(), this)
    }

    override val argsSerializer: KSerializer<AIAgentToolArgs> = AIAgentToolArgs.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = agentName,
        description = agentDescription,
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "request",
                description = requestDescription,
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun execute(args: AIAgentToolArgs): AIAgentToolResult {
        try {
            return AIAgentToolResult(
                successful = true,
                result = agent.runAndGetResult(args.request)
            )
        } catch (e: Throwable) {
            return AIAgentToolResult(
                successful = false,
                errorMessage = "Error happened: ${e::class.simpleName}(${e.message})\n" +
                        e.stackTraceToString().take(100)
            )
        }
    }
}
