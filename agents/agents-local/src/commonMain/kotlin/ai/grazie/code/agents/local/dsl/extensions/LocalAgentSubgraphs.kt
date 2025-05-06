package ai.grazie.code.agents.local.dsl.extensions

import ai.grazie.code.agents.core.tools.*
import ai.grazie.code.agents.local.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.local.dsl.builders.LocalAgentSubgraphBuilderBase
import ai.grazie.code.agents.local.dsl.builders.LocalAgentSubgraphDelegate
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.graph.ToolSelectionStrategy
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import ai.jetbrains.code.prompt.params.LLMParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.jvm.JvmInline

internal suspend fun LocalAgentStageContext.promptWithTLDR(
    systemMessage: String,
    shouldTLDRHistory: Boolean = true,
    model: LLModel? = null,
    params: LLMParams? = null,
) {
    llm.writeSession {
        if (shouldTLDRHistory) replaceHistoryWithTLDR()
        rewritePrompt { prompt ->
            prompt.copy(
                messages = prompt.messages.filterNot { it is Message.System },
                model = model ?: prompt.model,
                params = params ?: prompt.params,
            )
        }
        updatePrompt {
            system(systemMessage)
        }
    }
}

/**
 * The result which subgraphs can return.
 */
@Serializable
sealed interface SubgraphResult : Tool.Args, ToolResult

@Serializable
data class VerifiedSubgraphResult(
    val correct: Boolean,
    val message: String,
) : SubgraphResult {
    override fun toStringDefault() = Json.encodeToString(serializer(), this)
}

@JvmInline
@Serializable
value class StringSubgraphResult(val result: String) : SubgraphResult {
    override fun toStringDefault() = Json.encodeToString(serializer(), this)
}


abstract class ProvideSubgraphResult<FinalResult : SubgraphResult> : Tool<FinalResult, FinalResult>()

object ProvideVerifiedSubgraphResult : ProvideSubgraphResult<VerifiedSubgraphResult>() {
    override val argsSerializer = VerifiedSubgraphResult.serializer()

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

object ProvideStringSubgraphResult : ProvideSubgraphResult<StringSubgraphResult>() {
    override val argsSerializer = StringSubgraphResult.serializer()

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
fun <ProvidedResult : SubgraphResult, Input> LocalAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    finishTool: ProvideSubgraphResult<ProvidedResult>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend LocalAgentStageContext.(input: Input) -> String
): LocalAgentSubgraphDelegate<Input, ProvidedResult> = subgraph(toolSelectionStrategy = toolSelectionStrategy) {
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
        }
    }

    val preFinish by node<ProvidedResult, ProvidedResult> { input ->
        llm.writeSession {
            unsetToolChoice()
        }
        input
    }


    val sendInput by nodeLLMSendStageInput()
    val callTool by nodeExecuteTool()
    val sendToolResult by nodeLLMSendToolResult()

    edge(nodeStart forwardTo defineTaskNode)
    edge(defineTaskNode forwardTo sendInput)
    edge(sendInput forwardTo preFinish onToolCall (finishTool) transformed {
        Json.decodeFromJsonElement(finishTool.argsSerializer, it.contentJson)
    })
    edge(sendInput forwardTo callTool onToolNotCalled (finishTool))

    edge(callTool forwardTo sendToolResult)

    edge(sendToolResult forwardTo preFinish onToolCall (finishTool) transformed {
        Json.decodeFromJsonElement(finishTool.argsSerializer, it.contentJson)
    })
    edge(sendToolResult forwardTo callTool onToolNotCalled (finishTool))

    edge(preFinish forwardTo nodeFinish)
}

@Suppress("unused")
fun <ProvidedResult : SubgraphResult, Input> LocalAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    finishTool: ProvideSubgraphResult<ProvidedResult>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend LocalAgentStageContext.(input: Input) -> String
) = subgraphWithTask(
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
fun <Input> LocalAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    toolSelectionStrategy: ToolSelectionStrategy,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend LocalAgentStageContext.(input: Input) -> String
) = subgraphWithTask(
    toolSelectionStrategy = toolSelectionStrategy,
    finishTool = ProvideStringSubgraphResult,
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)

@Suppress("unused")
fun <Input> LocalAgentSubgraphBuilderBase<*, *>.subgraphWithTask(
    tools: List<Tool<*, *>>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend LocalAgentStageContext.(input: Input) -> String
): LocalAgentSubgraphDelegate<Input, StringSubgraphResult> = subgraphWithTask(
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
fun <Input> LocalAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    toolSelectionStrategy: ToolSelectionStrategy,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend LocalAgentStageContext.(input: Input) -> String
): LocalAgentSubgraphDelegate<Input, VerifiedSubgraphResult> = subgraphWithTask(
    finishTool = ProvideVerifiedSubgraphResult,
    toolSelectionStrategy = toolSelectionStrategy,
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)

@Suppress("unused")
fun <Input> LocalAgentSubgraphBuilderBase<*, *>.subgraphWithVerification(
    tools: List<Tool<*, *>>,
    model: LLModel? = null,
    params: LLMParams? = null,
    shouldTLDRHistory: Boolean = true,
    defineTask: suspend LocalAgentStageContext.(input: Input) -> String
): LocalAgentSubgraphDelegate<Input, VerifiedSubgraphResult> = subgraphWithVerification(
    toolSelectionStrategy = ToolSelectionStrategy.Tools(tools.map { it.descriptor }),
    model = model,
    params = params,
    shouldTLDRHistory = shouldTLDRHistory,
    defineTask = defineTask
)