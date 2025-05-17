package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolArgs
import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.agent.AIAgentBase
import ai.koog.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

public fun AIAgentBase.asTool(
    agentDescription: String,
    name: String? = null,
    requestDescription: String = "Input for the task"
): Tool<AgentToolArgs, AgentToolResult> = AIAgentTool(
    agent = this,
    agentName = name ?: this::class.simpleName!!.lowercase(),
    requestDescription = requestDescription,
    agentDescription = agentDescription
)


public class AIAgentTool(
    private val agent: AIAgentBase,
    agentName: String,
    agentDescription: String,
    requestDescription: String = "Input for the task"
) : Tool<AgentToolArgs, AgentToolResult>() {
    @Serializable
    public data class AgentToolArgs(val request: String) : Args

    @Serializable
    public data class AgentToolResult(
        val successful: Boolean,
        val errorMessage: String? = null,
        val result: String? = null
    ) : ToolResult {
        override fun toStringDefault(): String = Json.encodeToString(serializer(), this)
    }

    override val argsSerializer: KSerializer<AgentToolArgs> = AgentToolArgs.serializer()

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

    override suspend fun execute(args: AgentToolArgs): AgentToolResult {
        try {
            return AgentToolResult(
                successful = true,
                result = agent.runAndGetResult(args.request)
            )
        } catch (e: Throwable) {
            return AgentToolResult(
                successful = false,
                errorMessage = "Error happened: ${e::class.simpleName}(${e.message})\n" +
                        e.stackTraceToString().take(100)
            )
        }
    }
}
