package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentContextBase
import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegateBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.dsl.extension.onToolNotCalled
import ai.koog.agents.core.dsl.extension.replaceHistoryWithTLDR
import ai.koog.agents.core.dsl.extension.setToolChoiceRequired
import ai.koog.agents.core.dsl.extension.unsetToolChoice
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.*
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal suspend fun AIAgentContextBase.promptWithTLDR(
    systemMessage: String,
    shouldTLDRHistory: Boolean = true,
    model: LLModel? = null,
    params: LLMParams? = null,
) {
    llm.writeSession {
        if (shouldTLDRHistory) replaceHistoryWithTLDR()
        rewritePrompt { prompt ->
            prompt
                .withMessages { messages -> messages.filterNot { it is Message.System } }
                .withParams(params ?: prompt.params)
        }
        if (model != null) changeModel(model)

        updatePrompt {
            system(systemMessage)
        }
    }
}

/**
 * The result which subgraphs can return.
 */
public interface SubgraphResult : Tool.Args, ToolResult

/**
 * The result which subgraphs can return.
 */
public interface SerializableSubgraphResult<T : SerializableSubgraphResult<T>> : Tool.Args, ToolResult.JSONSerializable<T>

@Serializable
public data class VerifiedSubgraphResult(
    val correct: Boolean,
    val message: String,
) : SubgraphResult {
    override fun toStringDefault(): String = Json.encodeToString(serializer(), this)
}

@Serializable
public data class StringSubgraphResult(public val result: String) : SubgraphResult {
    override fun toStringDefault(): String = Json.encodeToString(serializer(), this)
}

public abstract class ProvideSubgraphResult<FinalResult : SubgraphResult> : Tool<FinalResult, FinalResult>()

public object ProvideVerifiedSubgraphResult : ProvideSubgraphResult<VerifiedSubgraphResult>() {
    override val argsSerializer: KSerializer<VerifiedSubgraphResult> = VerifiedSubgraphResult.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "finish_task_execution",
        description = "Please call this tool after you are sure that the task is completed. Verify if the task was completed correctly and provide additional information if there are problems.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "correct",
                description = "Verification result. True if task is executed correctly, false if incorrect",
                type = ToolParameterType.Boolean
            ),
            ToolParameterDescriptor(
                name = "message",
                description = "Summary of the task verification. Please provide a brief description of all the problems in this project if the task was failed",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun execute(args: VerifiedSubgraphResult): VerifiedSubgraphResult {
        return args
    }
}

public object ProvideStringSubgraphResult : ProvideSubgraphResult<StringSubgraphResult>() {
    override val argsSerializer: KSerializer<StringSubgraphResult> = StringSubgraphResult.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "finish_task_execution",
        description = "Please call this tool after you are sure that the task is completed. Verify if the task was completed correctly and provide additional information if there are problems.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "result",
                description = "Result of the given task",
                type = ToolParameterType.String
            )
        )
    )

    override suspend fun execute(args: StringSubgraphResult): StringSubgraphResult {
        return args
    }
}

/**
 * Creates a subgraph, which performs one specific task, defined by [defineTask],
 * using the tools defined by [toolSelectionStrategy].
 * When LLM believes that the task is finished, it will call [finishTool], generating [ProvidedResult] as its argument.
 * The generated [ProvidedResult] is the result of this subgraph.
 *
 * Use this function if you need the agent to perform a single task which outputs a structured result.
 *
 * @property toolSelectionStrategy Strategy to select tools available to the LLM during this task
 * @property finishTool The tool which LLM must call in order to complete the task.
 * The tool interface here is used as a descriptor of the structured result that LLM must produce.
 * The tool itself is never called.
 * @property model LLM used for this task
 * @property params Specific LLM parameters for this task
 * @property shouldTLDRHistory Whether to compress the history when starting to execute this task
 * @property defineTask A block which defines the task. It may just return a system prompt for the task,
 * but may also alter agent context, prompt, storage, etc.
 */
public fun <Input, ProvidedResult : SubgraphResult> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    finishTool: ProvideSubgraphResult<ProvidedResult>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegateBase<Input, ProvidedResult> = subgraph(toolSelectionStrategy = toolSelectionStrategy) {
    val defineTaskNode by node<Input, Unit> { input ->
        val task = defineTask(input)
        promptWithTLDR(
            task,
            shouldTLDRHistory,
            model,
            params,
        )

        llm.writeSession {
            setToolChoiceRequired()

            if (finishTool.descriptor !in tools) {
                tools = tools + finishTool.descriptor
            }
        }
    }

    val preFinish by node<ProvidedResult, ProvidedResult> { input ->
        llm.writeSession {
            rewritePrompt {
                prompt.copy(
                    messages = prompt.messages.take(prompt.messages.size - 1)
                )
            }
            unsetToolChoice()
        }

        llm.writeSession {
            tools = tools - finishTool.descriptor
        }

        input
    }

    val nodeCallLLM by nodeLLMRequest()
    val callTool by nodeExecuteTool()
    val sendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo defineTaskNode)
    edge(defineTaskNode forwardTo nodeCallLLM transformed { agentInput })
    edge(nodeCallLLM forwardTo preFinish onToolCall (finishTool) transformed {
        Json.decodeFromJsonElement(finishTool.argsSerializer, it.contentJson)
    })
    edge(nodeCallLLM forwardTo callTool onToolNotCalled (finishTool))

    edge(callTool forwardTo sendToolResult)

    edge(sendToolResult forwardTo preFinish onToolCall (finishTool) transformed {
        Json.decodeFromJsonElement(finishTool.argsSerializer, it.contentJson)
    })
    edge(sendToolResult forwardTo callTool onToolNotCalled (finishTool))

    edge(preFinish forwardTo nodeFinish)
}

@Suppress("unused")
public fun <Input, ProvidedResult : SubgraphResult> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    finishTool: ProvideSubgraphResult<ProvidedResult>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegateBase<Input, ProvidedResult> = subgraphWithTask(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    finishTool = finishTool,
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)

/**
 * [subgraphWithTask] with [StringSubgraphResult] result.
 */
@Suppress("unused")
public fun <Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegateBase<Input, StringSubgraphResult> = subgraphWithTask(
    toolSelectionStrategy = toolSelectionStrategy,
    finishTool = ProvideStringSubgraphResult,
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)

@Suppress("unused")
public fun <Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegateBase<Input, StringSubgraphResult> = subgraphWithTask(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)

/**
 * [subgraphWithTask] with [VerifiedSubgraphResult] result.
 * It verifies if the task was performed correctly or not, and describes the problems if any.
 */
@Suppress("unused")
public fun <Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    toolSelectionStrategy: ToolSelectionStrategy,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegateBase<Input, VerifiedSubgraphResult> = subgraphWithTask(
    finishTool = ProvideVerifiedSubgraphResult,
    toolSelectionStrategy = toolSelectionStrategy,
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)

@Suppress("unused")
public fun <Input> AIAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    tools: List<Tool<*, *>>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend AIAgentContextBase.(input: Input) -> String
): AIAgentSubgraphDelegateBase<Input, VerifiedSubgraphResult> = subgraphWithVerification(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)
