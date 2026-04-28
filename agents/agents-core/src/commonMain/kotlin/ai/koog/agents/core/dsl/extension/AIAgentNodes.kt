@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.session.callTool
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * A pass-through node that does nothing and returns input as output
 *
 * @param name Optional node name, defaults to delegate's property name.
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> nodeDoNothing(
    name: String? = null
): AIAgentNodeDelegate<T, T> =
    node(name) { input -> input }

// ================
// Prompt nodes
// ================

/**
 * A node that adds messages to the LLM prompt using the provided prompt builder.
 * The input is passed as it is to the output.
 *
 * @param name Optional node name, defaults to delegate's property name.
 * @param body Lambda to modify the prompt using PromptBuilder.
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> nodeAppendPrompt(
    name: String? = null,
    noinline body: PromptBuilder.() -> Unit
): AIAgentNodeDelegate<T, T> =
    node(name) { input ->
        appendPromptImpl(input, body)
    }

/**
 * [InternalAgentsApi] method. Appends a prompt to the current LLM session.
 *
 * @param input The input object to be used for the operation. It serves as both input and output of this method.
 * @param body A lambda to customize the construction of the prompt using the [PromptBuilder].
 * @return The same input object, allowing for fluent usage patterns or further chaining.
 */
@InternalAgentsApi
public suspend fun <T> AIAgentGraphContextBase.appendPromptImpl(
    input: T,
    body: PromptBuilder.() -> Unit
): T {
    llm.writeSession {
        appendPrompt {
            body()
        }
    }

    return input
}

// ================
// LLM Request string nodes
// ================

//region LLMRequest

@AIAgentBuilderDslMarker
public fun nodeLLMRequestWithUserText(
    name: String? = null,
): AIAgentNodeDelegate<String, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestLLM()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestOnlyCallingToolsWithUserText(
    name: String? = null,
): AIAgentNodeDelegate<String, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestLLMOnlyCallingTools()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestWithoutToolsWithUserText(
    name: String? = null,
): AIAgentNodeDelegate<String, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestLLMWithoutTools()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestForceOneToolWithUserText(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegate<String, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestLLMForceOneTool(tool)
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestMultipleChoicesWithUserText(
    name: String? = null,
): AIAgentNodeDelegate<String, LLMChoice> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestLLMMultipleChoices()
        }
    }

// Region Streaming

@AIAgentBuilderDslMarker
public fun <T> nodeLLMRequestStreamingWithUserText(
    name: String? = null,
    structureDefinition: StructureDefinition? = null,
    transformStreamData: suspend (Flow<StreamFrame>) -> Flow<T>
): AIAgentNodeDelegate<String, Flow<T>> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }
            requestStreaming(structureDefinition, transformStreamData)
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestStreamingWithUserText(
    name: String? = null,
    structureDefinition: StructureDefinition? = null,
): AIAgentNodeDelegate<String, Flow<StreamFrame>> =
    nodeLLMRequestStreamingWithUserText(name, structureDefinition) { it }

// Region Structured

@AIAgentBuilderDslMarker
public fun <T> nodeLLMRequestStructuredWithUserText(
    name: String? = null,
    config: StructuredRequestConfig<T>,
    fixingParser: StructureFixingParser? = null
): AIAgentNodeDelegate<String, Result<StructuredResponse<T>>> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMStructured(config, fixingParser)
        }
    }

@AIAgentBuilderDslMarker
public inline fun <reified T> nodeLLMRequestStructuredWithUserText(
    name: String? = null,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null
): AIAgentNodeDelegate<String, Result<StructuredResponse<T>>> = node(name) { message ->
    llm.writeSession {
        appendPrompt {
            user(message)
        }

        requestLLMStructured<T>(
            examples = examples,
            fixingParser = fixingParser
        )
    }
}

// ================
// LLM Request message nodes
// ================

//region LLMRequest

@AIAgentBuilderDslMarker
public fun nodeLLMRequest(
    name: String? = null,
): AIAgentNodeDelegate<Message.User, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }
            requestLLM()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestOnlyCallingTools(
    name: String? = null,
): AIAgentNodeDelegate<Message.User, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }
            requestLLMOnlyCallingTools()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestWithoutTools(
    name: String? = null,
): AIAgentNodeDelegate<Message.User, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }
            requestLLMWithoutTools()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegate<Message.User, Message.Assistant> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }
            requestLLMForceOneTool(tool)
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestMultipleChoices(
    name: String? = null,
): AIAgentNodeDelegate<Message.User, LLMChoice> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }
            requestLLMMultipleChoices()
        }
    }

// Region Streaming

/**
 * [InternalAgentsApi] method. Appends a message to the prompt and requests a streaming response from the LLM.
 *
 * @param input The user message to append to the prompt.
 * @param structureDefinition An optional structure definition to customize the streaming response.
 * @param transformStreamData A suspend function that transforms the incoming [StreamFrame] flow into a flow of type [T].
 * @return A [Flow] of [T] produced by the streaming LLM response after transformation.
 */
@InternalAgentsApi
public suspend fun <T> AIAgentGraphContextBase.requestStreamingImpl(
    input: Message.User,
    structureDefinition: StructureDefinition? = null,
    transformStreamData: suspend (Flow<StreamFrame>) -> Flow<T>
): Flow<T> = llm.writeSession {
    appendPrompt { message(input) }
    requestStreaming(structureDefinition, transformStreamData)
}

@AIAgentBuilderDslMarker
public fun <T> nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructureDefinition? = null,
    transformStreamData: suspend (Flow<StreamFrame>) -> Flow<T>
): AIAgentNodeDelegate<Message.User, Flow<T>> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }
            requestStreaming(structureDefinition, transformStreamData)
        }
    }

@AIAgentBuilderDslMarker
public fun nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructureDefinition? = null,
): AIAgentNodeDelegate<Message.User, Flow<StreamFrame>> = nodeLLMRequestStreaming(name, structureDefinition) { it }

// Region Structured

@AIAgentBuilderDslMarker
public fun <T> nodeLLMRequestStructured(
    name: String? = null,
    config: StructuredRequestConfig<T>,
    fixingParser: StructureFixingParser? = null
): AIAgentNodeDelegate<Message.User, Result<StructuredResponse<T>>> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                message(message)
            }

            requestLLMStructured(config, fixingParser)
        }
    }

@AIAgentBuilderDslMarker
public inline fun <reified T> nodeLLMRequestStructured(
    name: String? = null,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null
): AIAgentNodeDelegate<Message.User, Result<StructuredResponse<T>>> = node(name) { message ->
    llm.writeSession {
        appendPrompt {
            message(message)
        }

        requestLLMStructured<T>(
            examples = examples,
            fixingParser = fixingParser
        )
    }
}

// Region Moderate

@OptIn(DetachedPromptExecutorAPI::class)
@AIAgentBuilderDslMarker
public fun nodeLLMModerateMessage(
    name: String? = null,
    moderatingModel: LLModel? = null,
    includeCurrentPrompt: Boolean = false,
): AIAgentNodeDelegate<Message, ModerationResult> =
    node<Message, ModerationResult>(name) { message ->
        val moderationPrompt = if (includeCurrentPrompt) {
            prompt(llm.prompt) { message(message) }
        } else {
            prompt("single-message-moderation") { message(message) }
        }

        val moderationResult = llm.promptExecutor.moderate(moderationPrompt, moderatingModel ?: llm.model)

        moderationResult
    }

// ================
// Compress history nodes
// ================

/**
 * A node that compresses the current LLM prompt (message history) into a summary, replacing messages with a TLDR.
 *
 * @param name Optional node name.
 * @param strategy Determines which messages to include in compression.
 * @param retrievalModel An optional [LLModel] that will be used for retrieval of the facts from memory.
 *                       By default, the same model will be used as the current one in the agent's strategy.
 * @param preserveMemory Specifies whether to retain message memory after compression.
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> nodeLLMCompressHistory(
    name: String? = null,
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    retrievalModel: LLModel? = null,
    preserveMemory: Boolean = true
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    llmCompressHistoryImpl(input, retrievalModel, strategy, preserveMemory)
}

/**
 * [InternalAgentsApi] method. Performs LLM history compression.
 *
 * @param retrievalModel The optional [LLModel] to be used temporarily for retrieval during history compression.
 * @param strategy The [HistoryCompressionStrategy] to be applied to compress the conversation history.
 * @param preserveMemory A flag indicating whether memory should be preserved, preventing permanent loss of history.
 * @param input The input of type [T] that will be passed through and returned unchanged by the method.
 * @return The input of type [T], passed through without modifications.
 */
@InternalAgentsApi
public suspend fun <T> AIAgentGraphContextBase.llmCompressHistoryImpl(
    input: T,
    retrievalModel: LLModel?,
    strategy: HistoryCompressionStrategy,
    preserveMemory: Boolean
): T {
    llm.writeSession {
        val initialModel = model
        if (retrievalModel != null) {
            model = retrievalModel
        }

        replaceHistoryWithTLDR(strategy, preserveMemory)

        model = initialModel
    }

    return input
}

// ================
// Execute Tool nodes
// ================

private suspend fun executeTools(
    environment: AIAgentEnvironment,
    toolCalls: List<MessagePart.Tool.Call>,
    parallel: Boolean
): List<ReceivedToolResult> {
    return buildList {
        if (parallel) {
            addAll(environment.executeTools(toolCalls))
        } else {
            toolCalls.forEach { toolCall ->
                val toolResult = environment.executeTool(toolCall)
                add(toolResult)
            }
        }
    }
}

@Serializable
public data class ToolCalls(
    val toolCalls: List<MessagePart.Tool.Call>
)

@Serializable
public data class ToolResults(
    val toolCalls: List<MessagePart.Tool.Result>
)

@Serializable
public data class ReceivedToolResults(
    val toolResults: List<ReceivedToolResult>
)

@AIAgentBuilderDslMarker
public fun nodeExecuteTools(
    name: String? = null,
    parallel: Boolean = false,
): AIAgentNodeDelegate<ToolCalls, Message.User> =
    node(name) {
        val parts = executeTools(environment, it.toolCalls, parallel)
        llm.writeSession {
            userMessage(parts.map { toolResult -> toolResult.toMessagePart() })
        }
    }

// Region ReceivedToolResult

@AIAgentBuilderDslMarker
public fun nodeExecuteToolsAndGetResults(
    name: String? = null,
    parallel: Boolean = false,
): AIAgentNodeDelegate<ToolCalls, ReceivedToolResults> =
    node(name) {
        ReceivedToolResults(executeTools(environment, it.toolCalls, parallel))
    }

@AIAgentBuilderDslMarker
public fun nodeSendToolReceivedResults(
    name: String? = null
): AIAgentNodeDelegate<ReceivedToolResults, Message.Assistant> =
    node(name) {
        llm.writeSession {
            appendPrompt {
                user {
                    it.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                }
            }

            requestLLM()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeSendToolReceivedResultsOnlyCallingTools(
    name: String? = null,
): AIAgentNodeDelegate<ReceivedToolResults, Message.Assistant> =
    node(name) {
        llm.writeSession {
            appendPrompt {
                user {
                    it.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                }
            }
            requestLLMOnlyCallingTools()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeSendToolReceivedResultsWithoutTools(
    name: String? = null,
): AIAgentNodeDelegate<ReceivedToolResults, Message.Assistant> =
    node(name) {
        llm.writeSession {
            appendPrompt {
                user {
                    it.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                }
            }
            requestLLMWithoutTools()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeSendToolReceivedResultsForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegate<ReceivedToolResults, Message.Assistant> =
    node(name) {
        llm.writeSession {
            appendPrompt {
                user {
                    it.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                }
            }
            requestLLMForceOneTool(tool)
        }
    }

@AIAgentBuilderDslMarker
public fun nodeSendToolReceivedResultsMultipleChoices(
    name: String? = null,
): AIAgentNodeDelegate<ReceivedToolResults, LLMChoice> =
    node(name) {
        llm.writeSession {
            appendPrompt {
                user {
                    it.toolResults.forEach { toolResult -> toolResult(toolResult.toMessagePart()) }
                }
            }
            requestLLMMultipleChoices()
        }
    }

@AIAgentBuilderDslMarker
public fun nodeExecuteSingleTool(
    name: String? = null,
    parallel: Boolean = false,
): AIAgentNodeDelegate<MessagePart.Tool.Call, ReceivedToolResult> =
    node(name) {
        environment.executeTool(it)
    }

/**
 * A node that calls a specific tool directly using the provided arguments.
 *
 * @param name Optional node name.
 * @param tool The tool to execute.
 * @param doAppendPrompt Specifies whether to add tool call details to the prompt.
 */
@AIAgentBuilderDslMarker
public inline fun <reified ToolArg, reified TResult> nodeExecuteSingleTool(
    name: String? = null,
    tool: Tool<ToolArg, TResult>,
    doAppendPrompt: Boolean = true
): AIAgentNodeDelegate<ToolArg, SafeTool.Result<TResult>> =
    node(name) { toolArgs ->
        executeSingleToolImpl(tool, toolArgs, doAppendPrompt)
    }

/**
 * [InternalAgentsApi] method. Executes a single tool with the provided arguments and returns the result.
 *
 * @param toolArgs The arguments to be passed to the tool during execution.
 * @param doAppendPrompt Indicates whether to append prompts to the LLM session for the tool call
 *                        and its result.
 * @param tool The tool to be invoked, containing the logic for processing the input arguments
 *             and producing the result.
 *
 * @return A [SafeTool.Result] containing the result of the tool execution.
 */
@InternalAgentsApi
public suspend fun <TResult, ToolArg> AIAgentGraphContextBase.executeSingleToolImpl(
    tool: Tool<ToolArg, TResult>,
    toolArgs: ToolArg,
    doAppendPrompt: Boolean
): SafeTool.Result<TResult> = llm.writeSession {
    if (doAppendPrompt) {
        appendPrompt {
            // Why not tool message? Because it requires id != null to send it back to the LLM,
            // The only workaround is to generate it
            user(
                "Tool call: ${tool.name} was explicitly called with args: ${
                    tool.encodeArgs(toolArgs, config.serializer)
                }"
            )
        }
    }

    val toolResult = callTool(tool, toolArgs)

    if (doAppendPrompt) {
        appendPrompt {
            user(
                "Tool call: ${tool.name} was explicitly called and returned result: ${
                    toolResult.content
                }"
            )
        }
    }

    toolResult
}

/**
 * Creates a node that sets up a structured output for an AI agent subgraph.
 *
 * The method defines a new node with a configurable structured output schema
 * that will be applied during the AI agent's message processing. The schema
 * is determined by the given configuration.
 *
 * @param name An optional name for the node. If null, a default name will be assigned.
 * @param config The configuration that defines the structured output format and schema.
 * @return An instance of [AIAgentNodeDelegate] representing the constructed node.
 */
@AIAgentBuilderDslMarker
public inline fun <reified TInput, T> nodeSetStructuredOutput(
    name: String? = null,
    config: StructuredRequestConfig<T>,
): AIAgentNodeDelegate<TInput, TInput> =
    node(name) { message ->
        setStructuredOutputImpl(config, message)
    }

/**
 * [InternalAgentsApi] method. Sets up structured output for an AI agent subgraph.
 *
 * @param T The type of the structured output.
 * @param TInput The type of the input message.
 * @param config The configuration used to update the agent's prompt in the context.
 * @param message The input message to be processed and returned.
 * @return The input message after processing.
 */
@InternalAgentsApi
public suspend fun <T, TInput> AIAgentGraphContextBase.setStructuredOutputImpl(
    config: StructuredRequestConfig<T>,
    message: TInput
): TInput = llm.writeSession {
    prompt = config.updatePrompt(model, prompt)
    message
}
