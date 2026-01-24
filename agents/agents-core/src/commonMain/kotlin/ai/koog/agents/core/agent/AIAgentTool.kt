package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.asToolDescriptor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
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
 * @property agentService The AI agent service to create the agent.
 * @property agentName A unique name for the agent.
 * @property agentDescription A brief description of the agent's functionality.
 * @property inputDescription An optional description of the agent's input. Required for primitive types only!
 * If not specified for a primitive input type (ex: String, Int, ...), an empty input description will be sent to LLM.
 * Does not have any effect for non-primitive [Input] type with @LLMDescription annotations.
 * @property inputSerializer A serializer for converting the input type to/from JSON.
 * @property outputSerializer A serializer for converting the output type to/from JSON.
 * @param parentAgentId Optional ID of the parent AI agent. Tool agent IDs will be generated as "parentAgentId.<number of tool call>"
 */
public class AIAgentTool<Input, Output> @OptIn(InternalAgentToolsApi::class) constructor(
    private val agentService: AIAgentService<Input, Output, *>,
    private val agentName: String,
    private val agentDescription: String,
    private val inputDescription: String? = null,
    private val inputSerializer: KSerializer<Input>,
    private val outputSerializer: KSerializer<Output>,
    private val parentAgentId: String? = null
) : Tool<Input, AgentToolResult<Output>>(
    argsSerializer = inputSerializer,
    resultSerializer = AgentToolResult.serializer(outputSerializer),
    descriptor = inputSerializer.descriptor.asToolDescriptor(agentName, agentDescription, inputDescription)
) {
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
    @Serializable
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
