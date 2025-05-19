package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolArgs
import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.tools.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Converts the current AI agent into a tool that can be utilized with the specified parameters.
 *
 * @param agentDescription a descriptive text that explains the functionality or purpose of the agent
 * @param name an optional name for the tool; if not provided, the name will be derived from the agent's class name
 * @param requestDescription a description of the input expected for the created tool; defaults to "Input for the task"
 * @return a tool representation of the AI agent
 */
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

/**
 * AIAgentTool is a specialized tool that integrates an AI agent for processing tasks
 * by leveraging input arguments and producing corresponding results.
 *
 * This class extends the generic Tool interface with custom argument and result types.
 *
 * @constructor Creates an instance of AIAgentTool with the specified AI agent, its name,
 * description, and an optional description for the request parameter.
 *
 * @param agent The AI agent that implements the AIAgentBase interface and handles task execution.
 * @param agentName A name assigned to the tool that helps identify it.
 * @param agentDescription A brief description of what the tool does.
 * @param requestDescription An optional description of the input to the tool, defaulting to
 * "Input for the task".
 */
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
