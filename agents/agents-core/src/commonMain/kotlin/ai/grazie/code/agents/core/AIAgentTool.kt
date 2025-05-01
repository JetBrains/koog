package ai.grazie.code.agents.core

import ai.grazie.code.agents.core.AIAgentTool.AgentToolArgs
import ai.grazie.code.agents.core.AIAgentTool.AgentToolResult
import ai.grazie.code.agents.core.model.agent.AIAgentConfig
import ai.grazie.code.agents.core.model.agent.AIAgentStrategy
import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun <TStrategy : AIAgentStrategy<TConfig>, TConfig : AIAgentConfig> AIAgent<TStrategy, TConfig>.asTool(
    agentDescription: String,
    requestDescription: String = "Input for the task"
) = AIAgentTool<TStrategy, TConfig>(this, requestDescription, agentDescription)


class AIAgentTool<TStrategy : AIAgentStrategy<TConfig>, TConfig : AIAgentConfig>(
    val agent: AIAgent<TStrategy, TConfig>,
    agentName: String,
    agentDescription: String,
    requestDescription: String = "Input for the task"
) : Tool<AgentToolArgs, AgentToolResult>() {
    @Serializable
    data class AgentToolArgs(val request: String) : Tool.Args

    @Serializable
    data class AgentToolResult(
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
