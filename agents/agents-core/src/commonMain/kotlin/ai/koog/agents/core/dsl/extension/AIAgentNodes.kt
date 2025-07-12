package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredData
import ai.koog.prompt.structure.StructuredDataDefinition
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow

/**
 * A pass-through node that does nothing and returns input as output
 *
 * @param name Optional node name, defaults to delegate's property name.
 */
@AIAgentBuilderDslMarker
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeDoNothing(name: String? = null): AIAgentNodeDelegate<T, T> =
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
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeUpdatePrompt(
    name: String? = null,
    body: PromptBuilder.() -> Unit
): AIAgentNodeDelegate<T, T> =
    node(name) { input ->
        llm.writeSession {
            updatePrompt {
                body()
            }
        }

        input
    }

/**
 * A node that appends a user message to the LLM prompt and gets a response where the LLM can only call tools.
 *
 * @param name Optional name for the node.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageOnlyCallingTools(name: String? = null): AIAgentNodeDelegate<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMOnlyCallingTools()
        }
    }

/**
 * A node that that appends a user message to the LLM prompt and forces the LLM to use a specific tool.
 *
 * @param name Optional node name.
 * @param tool Tool descriptor the LLM is required to use.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegate<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

/**
 * A node that appends a user message to the LLM prompt and forces the LLM to use a specific tool.
 *
 * @param name Optional node name.
 * @param tool Tool the LLM is required to use.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: Tool<*, *>
): AIAgentNodeDelegate<String, Message.Response> =
    nodeLLMSendMessageForceOneTool(name, tool.descriptor)

/**
 * A node that appends a user message to the LLM prompt and gets a response with optional tool usage.
 *
 * @param name Optional node name.
 * @param allowToolCalls Controls whether LLM can use tools (default: true).
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequest(
    name: String? = null,
    allowToolCalls: Boolean = true
): AIAgentNodeDelegate<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            if (allowToolCalls) requestLLM()
            else requestLLMWithoutTools()
        }
    }

/**
 * Configures a node in the AI agent graph that performs prompt moderation using a specified language model.
 *
 * @param name The optional name of the moderation node. If not provided, an automatic one will be generated from the node variable name.
 * @param moderatingModel The optional language model to use for moderation. If not provided, a default model will be used.
 * @return A delegate representing the result of the moderation operation. It provides the moderation result for further processing.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMModeratePrompt(
    name: String? = null,
    moderatingModel: LLModel? = null
): AIAgentNodeDelegate<Any?, ModerationResult> =
    node(name) { _ ->
        llm.readSession {
            requestModeration()
        }
    }

/**
 * Represents a message that has undergone moderation and the result of the moderation.
 *
 * @property message The original message being moderated. This can include both the content and its metadata.
 * @property moderationResult The result of the moderation process applied to the message, indicating its*/
public data class ModeratedMessage(val message: Message, val moderationResult: ModerationResult)

/**
 * Adds a node to the AI agent subgraph for moderating messages using a specified language model.
 * This function creates a node that takes an input message and transforms it into a moderated message.
 *
 * @param name The optional name for the node.
 *             If not provided, an automatic one will be generated from the node variable name.
 * @param moderatingModel The optional language model to be used for moderation.
 *                        If null, a default or previously defined model will be applied.
 * @return A delegate for a node that processes a message into a moderated message.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMModerateMessage(
    name: String? = null,
    moderatingModel: LLModel? = null
): AIAgentNodeDelegate<Message, ModeratedMessage> =
    node(name) { message ->
        llm.readSession {
            ModeratedMessage(message, requestModeration())
        }
    }

/**
 * A node that appends a user message to the LLM prompt and requests structured data from the LLM with error correction capabilities.
 *
 * @param name Optional node name.
 * @param structure Definition of expected output format and parsing logic.
 * @param retries Number of retry attempts for failed generations.
 * @param fixingModel LLM used for error correction.
 */
@AIAgentBuilderDslMarker
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStructured(
    name: String? = null,
    structure: StructuredData<T>,
    retries: Int,
    fixingModel: LLModel
): AIAgentNodeDelegate<String, Result<StructuredResponse<T>>> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMStructured(
                structure,
                retries,
                fixingModel
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
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructuredDataDefinition? = null,
    transformStreamData: suspend (Flow<String>) -> Flow<T>
): AIAgentNodeDelegate<String, Flow<T>> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            val stream = requestLLMStreaming(structureDefinition)

            transformStreamData(stream)
        }
    }

/**
 * A node that appends a user message to the LLM prompt and streams LLM response without transformation.
 *
 * @param name Optional node name.
 * @param structureDefinition Optional structure to guide the LLM response.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructuredDataDefinition? = null,
): AIAgentNodeDelegate<String, Flow<String>> = nodeLLMRequestStreaming(name, structureDefinition) { it }

/**
 * A node that appends a user message to the LLM prompt and gets multiple LLM responses with tool calls enabled.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestMultiple(name: String? = null): AIAgentNodeDelegate<String, List<Message.Response>> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
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
 * @param preserveMemory Specifies whether to retain message memory after compression.
 */
@AIAgentBuilderDslMarker
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMCompressHistory(
    name: String? = null,
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
): AIAgentNodeDelegate<T, T> = node(name) { input ->
    llm.writeSession {
        replaceHistoryWithTLDR(strategy, preserveMemory)
    }

    input
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendToolResult(
    name: String? = null
): AIAgentNodeDelegate<ReceivedToolResult, Message.Response> =
    node(name) { result ->
        llm.writeSession {
            updatePrompt {
                tool {
                    result(result)
                }
            }

            requestLLM()
        }
    }

/**
 * A node that executes multiple tool calls. These calls can optionally be executed in parallel.
 *
 * @param name Optional node name.
 * @param parallelTools Specifies whether tools should be executed in parallel, defaults to false.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteMultipleTools(
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
 * A node that adds multiple tool results to the prompt and gets multiple LLM responses.
 *
 * @param name Optional node name.
 */
@AIAgentBuilderDslMarker
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMultipleToolResults(
    name: String? = null
): AIAgentNodeDelegate<List<ReceivedToolResult>, List<Message.Response>> =
    node(name) { results ->
        llm.writeSession {
            updatePrompt {
                tool {
                    results.forEach { result(it) }
                }
            }

            requestLLMMultiple()
        }
    }

/**
 * A node that calls a specific tool directly using the provided arguments.
 *
 * @param name Optional node name.
 * @param tool The tool to execute.
 * @param doUpdatePrompt Specifies whether to add tool call details to the prompt.
 */
@AIAgentBuilderDslMarker
public inline fun <reified ToolArg : ToolArgs, reified TResult : ToolResult> AIAgentSubgraphBuilderBase<*, *>.nodeExecuteSingleTool(
    name: String? = null,
    tool: Tool<ToolArg, TResult>,
    doUpdatePrompt: Boolean = true
): AIAgentNodeDelegate<ToolArg, SafeTool.Result<TResult>> =
    node(name) { toolArgs ->
        llm.writeSession {
            if (doUpdatePrompt) {
                updatePrompt {
                    // Why not tool message? Because it requires id != null to send it back to the LLM,
                    // The only workaround is to generate it
                    user(
                        "Tool call: ${tool.name} was explicitly called with args: ${
                            tool.encodeArgs(toolArgs)
                        }"
                    )
                }
            }

            val toolResult = callTool<ToolArg, TResult>(tool, toolArgs)

            if (doUpdatePrompt) {
                updatePrompt {
                    user(
                        "Tool call: ${tool.name} was explicitly called and returned result: ${
                            toolResult.content
                        }"
                    )
                }
            }

            toolResult
        }
    }
