package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.schema.getToolDescriptor
import ai.koog.serialization.KSerializerTypeToken
import ai.koog.serialization.TypeToken
import ai.koog.serialization.annotations.InternalKoogSerializationApi
import ai.koog.serialization.typeToken
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.coroutines.cancellation.CancellationException

/**
 * Converts the current AI agent into a tool to allow using it in other agents as a tool.
 *
 * @param agentName Agent name that would be a tool name for this agent tool.
 * @param agentDescription Agent description that would be a tool description for this agent tool.
 * @param inputDescription An optional description of the agent's input. Required for primitive types only!
 *  * If not specified for a primitive input type (ex: String, Int, ...), an empty input description will be sent to LLM.
 *  * Does not have any effect for non-primitive [Input] type with @LLMDescription annotations.
 * @param inputSerializer Serializer to deserialize tool arguments to agent input.
 * @param outputSerializer Serializer to serialize agent output to tool result.
 * @param json Optional [Json] instance to customize de/serialization behavior.
 * @return A special tool that wraps the agent functionality.
 */
@InternalAgentToolsApi
@Deprecated(
    level = DeprecationLevel.WARNING,
    message = "Please use `AIAgentService.createAgentTool(...)`, instead." +
        "Converting an instance of `AIAgent` into a tool is error-prone because `AIAgent` is essentially a single-use instance," +
        "while tools can be run multiple times, and moreover - in parallel - by another `AIAgent`. " +
        "That would cause an error."
)
public inline fun <reified Input, reified Output> AIAgent<Input, Output>.asTool(
    agentName: String,
    agentDescription: String,
    inputDescription: String? = null,
    inputSerializer: KSerializer<Input> = serializer(),
    outputSerializer: KSerializer<Output> = serializer(),
    json: Json = Json.Default,
): Tool<Input, AgentToolResult<Output>> {
    val service = when (this) {
        is GraphAIAgent -> AIAgentService.fromAgent(this)
        is FunctionalAIAgent -> AIAgentService.fromAgent(this)
        else -> throw UnsupportedOperationException("`asTool` can only be used for `GraphAIAgent` or `FunctionalAIAgent`")
    }

    @Suppress("DEPRECATION")
    return service.createAgentTool(
        agentName = agentName,
        agentDescription = agentDescription,
        inputDescription = inputDescription,
        inputSerializer = inputSerializer,
        outputSerializer = outputSerializer,
        parentAgentId = this.id
    )
}

/**
 * AIAgentTool is a generic tool that wraps an AI agent to facilitate integration
 * with the context of a tool execution framework. It enables the serialization,
 * deserialization, and execution of an AI agent's operations.
 *
 * @param Input The type of input expected by the AI agent.
 * @param Output The type of output produced by the AI agent.
 * @param agentService The AI agent service to create the agent.
 * @param agentName A unique name for the agent.
 * @param agentDescription A brief description of the agent's functionality.
 * If not specified for a primitive input type (ex: String, Int, ...), an empty input description will be sent to LLM.
 * Does not have any effect for non-primitive [Input] type with @LLMDescription annotations.
 * @param inputType Type token representing input type.
 * @param outputType Type token representing output type.
 * @param parentAgentId Optional ID of the parent AI agent. Tool agent IDs will be generated as "parentAgentId.<number of tool call>"
 */
public class AIAgentTool<Input, Output> @OptIn(InternalAgentToolsApi::class) constructor(
    private val agentService: AIAgentService<Input, Output, *>,
    private val agentName: String,
    private val agentDescription: String,
    private val inputType: TypeToken,
    private val outputType: TypeToken,
    private val parentAgentId: String? = null
) : Tool<Input, AgentToolResult<Output>>(
    argsType = inputType,
    resultType = typeToken(AgentToolResult::class, listOf(outputType)),
    descriptor = getToolDescriptor(inputType, agentName, agentDescription)
) {

    @Deprecated("Use constructor with TypeToken instead of KSerializer")
    @OptIn(InternalKoogSerializationApi::class)
    public constructor(
        agentService: AIAgentService<Input, Output, *>,
        agentName: String,
        agentDescription: String,
        inputSerializer: KSerializer<Input>,
        outputSerializer: KSerializer<Output>,
        parentAgentId: String? = null
    ) : this(
        agentService = agentService,
        agentName = agentName,
        agentDescription = agentDescription,
        inputType = KSerializerTypeToken(inputSerializer),
        outputType = KSerializerTypeToken(outputSerializer),
        parentAgentId = parentAgentId
    )

    @OptIn(ExperimentalAtomicApi::class)
    private val toolCallNumber: AtomicInt = AtomicInt(0)

    @OptIn(ExperimentalAtomicApi::class)
    private fun nextToolAgentID(): String = "$parentAgentId.${toolCallNumber.fetchAndIncrement()}"

    /**
     * Represents the result of executing an agent tool operation.
     *
     * @property successful Indicates whether the operation was successful.
     * @property errorMessage An optional error message describing the failure, if any.
     * @property result An optional agent tool result.
     */
    public data class AgentToolResult<Output>(
        val successful: Boolean,
        val errorMessage: String? = null,
        val result: Output? = null
    )

    @OptIn(InternalAgentToolsApi::class)
    override suspend fun execute(args: Input): AgentToolResult<Output> {
        return try {
            val result = agentService.createAgentAndRun(args, id = nextToolAgentID())

            AgentToolResult(
                successful = true,
                result = result,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AgentToolResult(
                successful = false,
                errorMessage = "Error happened: ${e::class.simpleName}(${e.message})\n${e.stackTraceToString().take(100)}"
            )
        }
    }
}
