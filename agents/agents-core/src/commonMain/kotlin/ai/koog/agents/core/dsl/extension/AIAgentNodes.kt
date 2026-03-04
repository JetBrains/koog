@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.session.callTool
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponses
import ai.koog.prompt.structure.StructureDefinition
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
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
// Simple LLM nodes
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

/**
 * A node that adds messages to the LLM prompt using the provided prompt builder.
 * The input is passed as it is to the output.
 *
 * @param name Optional node name, defaults to delegate's property name.
 * @param body Lambda to modify the prompt using PromptBuilder.
 */
@AIAgentBuilderDslMarker
@Deprecated("Use nodeAppendPrompt instead", ReplaceWith("nodeAppendPrompt(name, body)"))
public inline fun <reified T> nodeUpdatePrompt(
    name: String? = null,
    noinline body: PromptBuilder.() -> Unit
): AIAgentNodeDelegate<T, T> = nodeAppendPrompt(name, body)

/**
 * A node that appends a user message to the LLM prompt and gets a response where the LLM can only call tools.
 *
 * @param name Optional name for the node.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequestOnlyCallingTools(
    name: String? = null
): AIAgentNodeDelegate<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMOnlyCallingTools()
        }
    }

/**
 * A node that appends a user message to the LLM prompt and gets a response where the LLM can only call tools.
 *
 * @param name Optional name for the node.
 */
@Deprecated(
    "Please use nodeLLMRequestOnlyCallingTools instead.",
    ReplaceWith("nodeLLMRequestOnlyCallingTools(name)")
)
@AIAgentBuilderDslMarker
public fun nodeLLMSendMessageOnlyCallingTools(
    name: String? = null
): AIAgentNodeDelegate<String, Message.Response> =
    nodeLLMRequestOnlyCallingTools(name)

/**
 * A node that appends a user message to the LLM prompt and gets multiple LLM responses where the LLM can only call tools.
 *
 * @param name Optional name for the node.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequestMultipleOnlyCallingTools(
    name: String? = null
): AIAgentNodeDelegate<String, List<Message.Response>> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMMultipleOnlyCallingTools()
        }
    }

/**
 * A node that that appends a user message to the LLM prompt and forces the LLM to use a specific tool.
 *
 * @param name Optional node name.
 * @param tool Tool descriptor the LLM is required to use.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequestForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegate<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

/**
 * A node that that appends a user message to the LLM prompt and forces the LLM to use a specific tool.
 *
 * @param name Optional node name.
 * @param tool Tool descriptor the LLM is required to use.
 */
@Deprecated(
    "Please use nodeLLMRequestForceOneTool instead.",
    ReplaceWith("nodeLLMRequestForceOneTool(name, tool)")
)
@AIAgentBuilderDslMarker
public fun nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegate<String, Message.Response> =
    nodeLLMRequestForceOneTool(name, tool)

/**
 * A node that appends a user message to the LLM prompt and forces the LLM to use a specific tool.
 *
 * @param name Optional node name.
 * @param tool Tool the LLM is required to use.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequestForceOneTool(
    name: String? = null,
    tool: Tool<*, *>
): AIAgentNodeDelegate<String, Message.Response> =
    nodeLLMRequestForceOneTool(name, tool.descriptor)

/**
 * A node that appends a user message to the LLM prompt and forces the LLM to use a specific tool.
 *
 * @param name Optional node name.
 * @param tool Tool the LLM is required to use.
 */
@Deprecated(
    "Please use nodeLLMRequestForceOneTool instead.",
    ReplaceWith("nodeLLMRequestForceOneTool(name, tool)")
)
@AIAgentBuilderDslMarker
public fun nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: Tool<*, *>
): AIAgentNodeDelegate<String, Message.Response> =
    nodeLLMRequestForceOneTool(name, tool)

/**
 * A node that appends a user message to the LLM prompt and gets a response with optional tool usage.
 *
 * @param name Optional node name.
 * @param allowToolCalls Controls whether LLM can use tools (default: true).
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequest(
    name: String? = null,
    allowToolCalls: Boolean = true,
): AIAgentNodeDelegate<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }

            if (allowToolCalls) {
                requestLLM()
            } else {
                requestLLMWithoutTools()
            }
        }
    }

/**
 * Represents a message that has undergone moderation and the result of the moderation.
 *
 * @property message The original message being moderated.
 * @property moderationResult The result of the moderation.
 * */
@Serializable
public data class ModeratedMessage(val message: Message, val moderationResult: ModerationResult)

/**
 * A node that moderates only a single input message using a specified language model.
 *
 * @param name Optional node name, defaults to delegate's property name.
 * @param moderatingModel The optional language model to be used for moderation.
 * If null, a default or previously defined model will be applied.
 * @param includeCurrentPrompt Should current prompt be included in the moderation requests or only the input message.
 */
@OptIn(DetachedPromptExecutorAPI::class)
@AIAgentBuilderDslMarker
public fun nodeLLMModerateMessage(
    name: String? = null,
    moderatingModel: LLModel? = null,
    includeCurrentPrompt: Boolean = false,
): AIAgentNodeDelegate<Message, ModeratedMessage> =
    node<Message, ModeratedMessage>(name) { message ->
        val moderationPrompt = if (includeCurrentPrompt) {
            prompt(llm.prompt) { message(message) }
        } else {
            prompt("single-message-moderation") { message(message) }
        }

        val moderationResult = llm.promptExecutor.moderate(moderationPrompt, moderatingModel ?: llm.model)

        ModeratedMessage(message, moderationResult)
    }

/**
 * A node that appends a user message to the LLM prompt and requests structured data from the LLM with optional error
 * correction capabilities.
 *
 * @param name Optional node name.
 * @param config A configuration defining structures and behavior.
 */
@AIAgentBuilderDslMarker
public fun <T> nodeLLMRequestStructured(
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

/**
 * A node that appends a user message to the LLM prompt and requests structured data from the LLM with optional error
 * correction capabilities.
 *
 * This is a simple version of the full `nodeLLMRequestStructured`. Unlike the full version, it does not require specifying
 * struct definitions and structured output modes manually. It attempts to find the best approach to provide a structured
 * output based on the defined model capabilities.
 *
 * @param name Optional node name.
 * @param examples Optional list of examples in case manual mode will be used. These examples might help the model to
 * understand the format better.
 * @param fixingParser Optional parser that handles malformed responses by using an auxiliary LLM to
 * intelligently fix parsing errors. When specified, parsing errors trigger additional
 * LLM calls with error context to attempt correction of the structure format.
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> nodeLLMRequestStructured(
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

/**
 * A node that appends a user message to the LLM prompt, streams LLM response and transforms the stream data.
 *
 * @param name Optional node name.
 * @param structureDefinition Optional structure to guide the LLM response.
 * @param transformStreamData Function to process the streamed data.
 */
@AIAgentBuilderDslMarker
public fun <T> nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructureDefinition? = null,
    transformStreamData: suspend (Flow<StreamFrame>) -> Flow<T>
): AIAgentNodeDelegate<String, Flow<T>> =
    node(name) { message ->
        requestStreamingImpl(message, structureDefinition, transformStreamData)
    }

/**
 * [InternalAgentsApi] method. Performs LLM streaming and transforms the stream data.
 *
 * @param message The message string representing user input or instructions to be used in the prompt.
 * @param structureDefinition An optional structure definition that defines how the data should be structured
 *        during the language model request. Can be null if no specific structure is required.
 * @param transformStreamData A suspending function that takes a flow of raw streaming data ([StreamFrame])
 *        and returns a flow of the transformed data of type [T].
 * @return A flow of transformed data of type [T], resulting from the processing of the streaming request.
 */
@InternalAgentsApi
public suspend fun <T> AIAgentGraphContextBase.requestStreamingImpl(
    message: String,
    structureDefinition: StructureDefinition?,
    transformStreamData: suspend (Flow<StreamFrame>) -> Flow<T>
): Flow<T> = llm.writeSession {
    appendPrompt {
        user(message)
    }

    val stream = requestLLMStreaming(structureDefinition)

    transformStreamData(stream)
}

/**
 * A node that appends a user message to the LLM prompt and streams LLM response without transformation.
 *
 * @param name Optional node name.
 * @param structureDefinition Optional structure to guide the LLM response.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructureDefinition? = null,
): AIAgentNodeDelegate<String, Flow<StreamFrame>> = nodeLLMRequestStreaming(name, structureDefinition) { it }

/**
 * A node that appends a user message to the LLM prompt and gets multiple LLM responses with tool calls enabled.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMRequestMultiple(
    name: String? = null
): AIAgentNodeDelegate<String, List<Message.Response>> =
    node(name) { message ->
        llm.writeSession {
            appendPrompt {
                user(message)
            }

            requestLLMMultiple()
        }
    }

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

/**
 * A node that performs LLM streaming, collects all stream frames, converts them to response messages,
 * and updates the prompt with the results.
 *
 * This node is useful when you want to:
 * - Stream responses from the LLM for real-time feedback
 * - Collect the complete streamed response as messages
 * - Automatically update the conversation history with the streamed responses
 *
 * The node will:
 * 1. Initiate a streaming request to the LLM
 * 2. Collect all stream frames (text, tool calls, etc.)
 * 3. Convert the collected frames into proper Message.Response objects
 * 4. Update the prompt with these messages for conversation continuity
 * 5. Return the collected messages
 *
 * @param T The type of input this node accepts (passed through without modification)
 * @param name Optional node name for identification in the agent graph
 * @param structureDefinition Optional structure definition to guide the LLM's response format
 * @return A node delegate that accepts input of type T and returns a list of response messages
 *
 * @see nodeLLMRequestStreaming for streaming without automatic prompt updates
 * @see ai.koog.agents.core.agent.session.AIAgentLLMWriteSession.requestLLMStreaming for the underlying streaming functionality
 */
@AIAgentBuilderDslMarker
public inline fun <reified T> nodeLLMRequestStreamingAndSendResults(
    name: String? = null,
    structureDefinition: StructureDefinition? = null
): AIAgentNodeDelegate<T, List<Message.Response>> = node(name) { input ->
    requestStreamingAndSendResultsImpl(structureDefinition)
}

/**
 * [InternalAgentsApi] method. Performs LLM streaming and sends the results to the prompt.
 *
 * @param structureDefinition The optional structure defining the format of the textual content
 *                             for the LLM streaming request. If null, a default structure is assumed.
 *                             This parameter facilitates the construction and customization of content
 *                             during the streaming process.
 * @return A list of [Message.Response] objects containing the processed results from the LLM streaming request.
 */
@InternalAgentsApi
public suspend fun AIAgentGraphContextBase.requestStreamingAndSendResultsImpl(structureDefinition: StructureDefinition?): List<Message.Response> =
    llm.writeSession {
        requestLLMStreaming(structureDefinition)
            .toList()
            .toMessageResponses()
            .also { appendPrompt { messages(it) } }
    }

// ==========
// Tool nodes
// ==========

/**
 * A node that executes a tool call and returns its result.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        environment.executeTool(toolCall)
    }

/**
 * A node that adds a tool result to the prompt and requests an LLM response.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMSendToolResult(
    name: String? = null
): AIAgentNodeDelegate<ReceivedToolResult, Message.Response> =
    node(name) { result ->
        llm.writeSession {
            appendPrompt {
                tool {
                    result(result)
                }
            }

            requestLLM()
        }
    }

/**
 * A node that adds a tool result to the prompt and gets an LLM response where the LLM can only call tools.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMSendToolResultOnlyCallingTools(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, Message.Response> =
    node(name) { results ->
        llm.writeSession {
            appendPrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMOnlyCallingTools()
        }
    }

/**
 * A node that executes multiple tool calls. These calls can optionally be executed in parallel.
 *
 * @param name Optional node name.
 * @param parallelTools Specifies whether tools should be executed in parallel, defaults to false.
 */
@AIAgentBuilderDslMarker
public fun nodeExecuteMultipleTools(
    name: String? = null,
    parallelTools: Boolean = false,
): AIAgentNodeDelegate<List<Message.Tool.Call>, List<ReceivedToolResult>> =
    node(name) { toolCalls ->
        if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
    }

/**
 * Creates a node in the AI agent subgraph that processes a collection of tool calls,
 * executes them, and sends back the results to the downstream process. The tools can
 * be executed either in parallel or sequentially based on the provided configuration.
 *
 * @param name An optional name for the node to be created. If not provided, a default name is used.
 * @param parallelTools A flag to determine if the tool calls should be executed concurrently.
 *                       If true, all tool calls are executed in parallel; otherwise, they are
 *                       executed sequentially. Default value is false.
 * @return An instance of [AIAgentNodeDelegate] that takes a list of tool calls as input
 *         and returns the corresponding list of tool responses.
 */
public fun nodeExecuteMultipleToolsAndSendResults(
    name: String? = null,
    parallelTools: Boolean = false,
): AIAgentNodeDelegate<List<Message.Tool.Call>, List<Message.Response>> =
    node(name) { toolCalls ->
        val results = if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }

        llm.writeSession {
            appendPrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultiple()
        }
    }

/**
 * A node that adds multiple tool results to the prompt and gets multiple LLM responses.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMSendMultipleToolResults(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, List<Message.Response>> =
    node(name) { results ->
        llm.writeSession {
            appendPrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultiple()
        }
    }

/**
 * A node that adds multiple tool results to the prompt and gets multiple LLM responses where the LLM can only call tools.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun nodeLLMSendMultipleToolResultsOnlyCallingTools(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, List<Message.Response>> =
    node(name) { results ->
        llm.writeSession {
            appendPrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultipleOnlyCallingTools()
        }
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

    val toolResult = callTool<ToolArg, TResult>(tool, toolArgs)

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
