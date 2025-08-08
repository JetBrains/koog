package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolArgs
import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Converts the current AI agent into a tool to allow using it in other agents as a tool.
 *
 * @param agentName Agent name that would be a tool name for this agent tool.
 * @param agentDescription Agent description that would be a tool description for this agent tool.
 * @param inputDescriptor Descriptor for the agent input.
 * @param inputSerializer Serializer to deserialize tool arguments to agent input.
 * @param outputSerializer Serializer to serialize agent output to tool result.
 * @param json Optional [Json] instance to customize de/serialization behavior.
 * @return A special tool that wraps the agent functionality.
 */
public inline fun <reified Input, reified Output> AIAgent<Input, Output>.asTool(
    agentName: String,
    agentDescription: String,
    inputDescriptor: ToolParameterDescriptor,
    inputSerializer: KSerializer<Input> = serializer(),
    outputSerializer: KSerializer<Output> = serializer(),
    json: Json = Json.Default,
): Tool<AgentToolArgs, AgentToolResult> = AIAgentTool(
    agent = this,
    agentName = agentName,
    agentDescription = agentDescription,
    inputDescriptor = inputDescriptor,
    inputSerializer = inputSerializer,
    outputSerializer = outputSerializer,
    json = json,
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
 */
public class AIAgentTool<Input, Output>(
    private val agent: AIAgent<Input, Output>,
    private val agentName: String,
    private val agentDescription: String,
    private val inputDescriptor: ToolParameterDescriptor,
    private val inputSerializer: KSerializer<Input>,
    private val outputSerializer: KSerializer<Output>,
    private val json: Json = Json.Default,
) : Tool<AgentToolArgs, AgentToolResult>() {
    /**
     * Represents the arguments required for the execution of an agent tool.
     * Wraps raw arguments.
     *
     * @property args The input the agent tool.
     */
    @Serializable
    public data class AgentToolArgs(val args: JsonObject) : ToolArgs

    /**
     * Represents the result of executing an agent tool operation.
     *
     * @property successful Indicates whether the operation was successful.
     * @property errorMessage An optional error message describing the failure, if any.
     * @property result An optional agent tool result.
     */
    @Serializable
    public data class AgentToolResult(
        val successful: Boolean,
        val errorMessage: String? = null,
        val result: JsonElement? = null
    ) : ToolResult.JSONSerializable<AgentToolResult> {
        override fun getSerializer(): KSerializer<AgentToolResult> = serializer()
    }

    override val argsSerializer: KSerializer<AgentToolArgs> = object : KSerializer<AgentToolArgs> {
        private val innerSerializer = JsonObject.serializer()

        override val descriptor: SerialDescriptor = innerSerializer.descriptor

        override fun deserialize(decoder: Decoder): AgentToolArgs {
            return AgentToolArgs(innerSerializer.deserialize(decoder))
        }

        override fun serialize(encoder: Encoder, value: AgentToolArgs) {
            innerSerializer.serialize(encoder, value.args)
        }
    }

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = agentName,
        description = agentDescription,
        requiredParameters = listOf(inputDescriptor)
    )

    override suspend fun execute(args: AgentToolArgs): AgentToolResult {
        return try {
            val input = json.decodeFromJsonElement(inputSerializer, args.args.getValue(inputDescriptor.name))
            val result = agent.run(input)

            AgentToolResult(
                successful = true,
                result = json.encodeToJsonElement(outputSerializer, result)
            )
        } catch (e: Throwable) {
            AgentToolResult(
                successful = false,
                errorMessage = "Error happened: ${e::class.simpleName}(${e.message})\n${e.stackTraceToString().take(
                    100
                )}"
            )
        }
    }
}
