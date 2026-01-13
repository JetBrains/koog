package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.ToolCalls
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.extension.containsToolCalls
import ai.koog.agents.core.dsl.extension.extractToolCalls
import ai.koog.agents.core.dsl.extension.requestLLM
import ai.koog.agents.core.dsl.extension.requestLLMMultiple
import ai.koog.agents.core.dsl.extension.sendMultipleToolResults
import ai.koog.agents.core.dsl.extension.sendToolResult
import ai.koog.agents.core.dsl.extension.setToolChoiceRequired
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.executeTools
import ai.koog.agents.core.environment.toSafeResult
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor

/**
 * Executes a subtask with validation and verification of the results.
 * The method defines a subtask for the AI agent using the provided input
 * and additional parameters and ensures that the output is evaluated
 * based on its correctness and feedback.
 *
 * @param Input The type of the input provided to the subtask.
 * @param input The input data for the subtask, which will be used to
 * create and execute the task.
 * @param tools An optional list of tools that can be used during
 * the execution of the subtask.
 * @param llmModel An optional parameter specifying the LLM model to be used for the subtask.
 * @param llmParams Optional configuration parameters for the LLM, such as temperature
 * and token limits.
 * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
 * @param assistantResponseRepeatMax An optional parameter specifying the maximum number of
 * retries for getting valid responses from the assistant.
 * @param responseProcessor An optional processor defining the post-processing of messages returned from the LLM.
 * @param defineTask A suspend function that defines the subtask as a string
 * based on the provided input.
 * @return A [CriticResult] object containing the verification status, feedback,
 * and the original input for the subtask.
 */
@OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
public suspend inline fun <reified Input> AIAgentFunctionalContext.subtaskWithVerification(
    input: Input,
    tools: List<Tool<*, *>>? = null,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    responseProcessor: ResponseProcessor? = null,
    defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
): CriticResult<Input> {
    val result = subtask<Input, CriticResultFromLLM>(
        input,
        tools,
        llmModel,
        llmParams,
        runMode,
        assistantResponseRepeatMax,
        responseProcessor,
        defineTask
    )

    return CriticResult(
        successful = result.isCorrect,
        feedback = result.feedback,
        input = input
    )
}

/**
 * Executes a subtask within the larger context of an AI agent's functional operation. This method allows you to define a specific
 * task to be performed, using the given input, tools, and optional configuration parameters.
 *
 * @param Input The type of input provided to the subtask.
 * @param Output The type of the output expected from the subtask.
 * @param input The input data required for the subtask execution.
 * @param tools A list of tools available for use within the subtask.
 * @param llmModel The optional large language model to be used during the subtask, if different from the default one.
 * @param llmParams The configuration parameters for the large language model, such as temperature, etc.
 * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
 * @param assistantResponseRepeatMax The maximum number of times the assistant response can repeat. Useful to control redundant outputs.
 * @param responseProcessor An optional processor defining the post-processing of messages returned from the LLM.
 * @param defineTask A suspendable lambda defining the actual task logic, which takes the provided input and produces a task description.
 * @return The result of the subtask execution, as an instance of type Output.
 */
@OptIn(InternalAgentToolsApi::class)
public suspend inline fun <reified Input, reified Output> AIAgentFunctionalContext.subtask(
    input: Input,
    tools: List<Tool<*, *>>? = null,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    responseProcessor: ResponseProcessor? = null,
    defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
): Output {
    val finishTool = identityTool<Output>()

    return subtask(input, tools, finishTool, llmModel, llmParams, runMode, assistantResponseRepeatMax, responseProcessor, defineTask)
}

/**
 * Executes a subtask within the AI agent's functional context. This method enables the use of tools to
 * achieve a specific task based on the input provided. It defines the task using an inline function,
 * employs tools iteratively, and attempts to complete the subtask with a designated finishing tool.
 *
 * @param input The input data required to define and execute the subtask.
 * @param tools An optional list of tools that can be used to achieve the task, excluding the finishing tool.
 * @param finishTool A mandatory tool that determines the final result of the subtask by producing and transforming output.
 * @param llmModel An optional specific language learning model (LLM) to use for executing the subtask.
 * @param llmParams Optional parameters for configuring the behavior of the LLM during subtask execution.
 * @param runMode The mode in which tools should be executed, either sequentially or in parallel.
 * @param assistantResponseRepeatMax The maximum number of feedback attempts allowed from the language model if the subtask is not completed.
 * @param responseProcessor An optional processor defining the post-processing of messages returned from the LLM.
 * @param defineTask A suspendable function used to define the task based on the provided input.
 * @return The transformed final result of executing the finishing tool to complete the subtask.
 */
@OptIn(InternalAgentToolsApi::class, DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
public suspend inline fun <reified Input, reified Output, reified OutputTransformed> AIAgentFunctionalContext.subtask(
    input: Input,
    tools: List<Tool<*, *>>? = null,
    finishTool: Tool<Output, OutputTransformed>,
    llmModel: LLModel? = null,
    llmParams: LLMParams? = null,
    runMode: ToolCalls = ToolCalls.SEQUENTIAL,
    assistantResponseRepeatMax: Int? = null,
    responseProcessor: ResponseProcessor? = null,
    defineTask: suspend AIAgentFunctionalContext.(input: Input) -> String
): OutputTransformed {
    val maxAssistantResponses = assistantResponseRepeatMax ?: SubgraphWithTaskUtils.ASSISTANT_RESPONSE_REPEAT_MAX

    val toolsSubset = tools?.map { it.descriptor } ?: llm.readSession { this.tools.toList() }

    val originalTools = llm.readSession { this.tools.toList() }
    val originalModel = llm.readSession { this.model }
    val originalParams = llm.readSession { this.prompt.params }
    val originalResponseProcessor = llm.readSession { this.responseProcessor }

    // setup:
    llm.writeSession {
        if (finishTool.descriptor !in toolsSubset) {
            this.tools = toolsSubset + finishTool.descriptor
        }

        if (llmModel != null) {
            model = llmModel
        }

        if (llmParams != null) {
            prompt = prompt.withParams(llmParams)
        }

        if (responseProcessor != null) {
            this.responseProcessor = responseProcessor
        }

        setToolChoiceRequired()
    }

    val task = defineTask(input)

    val result = when (runMode) {
        ToolCalls.SINGLE_RUN_SEQUENTIAL -> subtaskWithSingleToolMode(
            task,
            finishTool,
            maxAssistantResponses
        )

        else -> subtaskWithMultiToolMode(
            task,
            finishTool,
            runMode,
            maxAssistantResponses
        )
    }

    // rollback
    llm.writeSession {
        this.tools = originalTools
        this.model = originalModel
        this.prompt = prompt.withParams(originalParams)
        this.responseProcessor = originalResponseProcessor
    }

    return result
}

@PublishedApi
@OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
internal suspend inline fun <reified Output, reified OutputTransformed> AIAgentFunctionalContext.subtaskWithMultiToolMode(
    task: String,
    finishTool: Tool<Output, OutputTransformed>,
    runMode: ToolCalls,
    maxAssistantResponses: Int
): OutputTransformed {
    var feedbacksCount = 0
    var responses = requestLLMMultiple(task)
    while (true) {
        when {
            responses.containsToolCalls() -> {
                val toolCalls = extractToolCalls(responses)
                val toolResults =
                    executeMultipleToolsHacked(toolCalls, finishTool, parallelTools = runMode == ToolCalls.PARALLEL)

                toolResults.firstOrNull { it.tool == finishTool.descriptor.name }
                    ?.let { finishResult ->
                        return finishResult.toSafeResult(finishTool).asSuccessful().result
                    }

                responses = sendMultipleToolResults(toolResults)
            }

            else -> {
                if (feedbacksCount++ > maxAssistantResponses) {
                    error(
                        "Unable to finish subtask. Reason: the model '${llm.model.id}' does not support tool choice, " +
                            "and was not able to call `${finishTool.name}` tool after " +
                            "<$maxAssistantResponses> attempts."
                    )
                }

                responses = requestLLMMultiple(
                    message = markdown {
                        h1("DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.")
                        h2("IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!")
                    }
                )
            }
        }
    }
}

@PublishedApi
@OptIn(DetachedPromptExecutorAPI::class, InternalAgentsApi::class)
internal suspend inline fun <reified Output, reified OutputTransformed> AIAgentFunctionalContext.subtaskWithSingleToolMode(
    task: String,
    finishTool: Tool<Output, OutputTransformed>,
    maxAssistantResponses: Int
): OutputTransformed {
    var feedbacksCount = 0
    var response = requestLLM(task)
    while (true) {
        when {
            response is Message.Tool.Call -> {
                val toolResult = executeToolHacked(response, finishTool)

                if (toolResult.tool == finishTool.descriptor.name) {
                    return toolResult.toSafeResult(finishTool).asSuccessful().result
                }

                response = sendToolResult(toolResult)
            }

            else -> {
                if (feedbacksCount++ > maxAssistantResponses) {
                    error(
                        "Unable to finish subtask. Reason: the model '${llm.model.id}' does not support tool choice, " +
                            "and was not able to call `${finishTool.name}` tool after " +
                            "<$maxAssistantResponses> attempts."
                    )
                }

                response = requestLLM(
                    message = markdown {
                        h1("DO NOT CHAT WITH ME DIRECTLY! CALL TOOLS, INSTEAD.")
                        h2("IF YOU HAVE FINISHED, CALL `${finishTool.name}` TOOL!")
                    }
                )
            }
        }
    }
}

@OptIn(InternalAgentToolsApi::class, InternalAgentsApi::class)
@PublishedApi
internal suspend inline fun <reified Output, reified OutputTransformed> AIAgentFunctionalContext.executeMultipleToolsHacked(
    toolCalls: List<Message.Tool.Call>,
    finishTool: Tool<Output, OutputTransformed>,
    parallelTools: Boolean = false
): List<ReceivedToolResult> {
    val finishTools = toolCalls.filter { it.tool == finishTool.descriptor.name }
    val normalTools = toolCalls.filterNot { it.tool == finishTool.descriptor.name }

    val finishToolResults = finishTools.map { toolCall ->
        executeFinishTool(toolCall, finishTool)
    }

    val normalToolResults = if (parallelTools) {
        environment.executeTools(normalTools)
    } else {
        normalTools.map { environment.executeTool(it) }
    }

    return finishToolResults + normalToolResults
}

@OptIn(InternalAgentToolsApi::class)
@PublishedApi
internal suspend inline fun <reified Output, reified OutputTransformed> AIAgentFunctionalContext.executeToolHacked(
    toolCall: Message.Tool.Call,
    finishTool: Tool<Output, OutputTransformed>
): ReceivedToolResult = executeMultipleToolsHacked(listOf(toolCall), finishTool).first()
