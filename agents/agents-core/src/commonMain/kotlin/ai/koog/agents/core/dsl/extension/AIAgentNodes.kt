package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.entity.AIAgentNodeBase
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegateBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.structure.StructuredData
import ai.koog.prompt.structure.StructuredDataDefinition
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.dsl.PromptBuilder
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * A pass-through node that does nothing and returns input as output
 *
 * @param name Optional node name, defaults to delegate's property name.
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeDoNothing(name: String? = null): AIAgentNodeDelegateBase<T, T> =
    node(name) { input -> input }

// ================
// Simple LLM nodes
// ================

/**
 * A node that adds messages to the LLM prompt using the provided prompt builder.
 *
 * @param name Optional node name, defaults to delegate's property name.
 * @param body Lambda to modify the prompt using PromptBuilder.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeUpdatePrompt(
    name: String? = null,
    body: PromptBuilder.() -> Unit
): AIAgentNodeDelegateBase<Unit, Unit> =
    node(name) {
        llm.writeSession {
            updatePrompt {
                body()
            }
        }
    }

/**
 * A node that appends a user message to the LLM prompt and gets a response where the LLM can only call tools.
 *
 * @param name Optional name for the node.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageOnlyCallingTools(name: String? = null): AIAgentNodeDelegateBase<String, Message.Response> =
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): AIAgentNodeDelegateBase<String, Message.Response> =
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: Tool<*, *>
): AIAgentNodeDelegateBase<String, Message.Response> =
    nodeLLMSendMessageForceOneTool(name, tool.descriptor)

/**
 * A node that appends a user message to the LLM prompt and gets a response with optional tool usage.
 *
 * @param name Optional node name.
 * @param allowToolCalls Controls whether LLM can use tools (default: true).
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequest(
    name: String? = null,
    allowToolCalls: Boolean = true
): AIAgentNodeDelegateBase<String, Message.Response> =
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
 * A node that appends a user message to the LLM prompt and requests structured data from the LLM with error correction capabilities.
 *
 * @param name Optional node name.
 * @param structure Definition of expected output format and parsing logic.
 * @param retries Number of retry attempts for failed generations.
 * @param fixingModel LLM used for error correction.
 */
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStructured(
    name: String? = null,
    structure: StructuredData<T>,
    retries: Int,
    fixingModel: LLModel
): AIAgentNodeDelegateBase<String, Result<StructuredResponse<T>>> =
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
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructuredDataDefinition? = null,
    transformStreamData: suspend (Flow<String>) -> Flow<T>
): AIAgentNodeDelegateBase<String, Flow<T>> =
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructuredDataDefinition? = null,
): AIAgentNodeDelegateBase<String, Flow<String>> = nodeLLMRequestStreaming(name, structureDefinition) { it }

/**
 * A node that appends a user message to the LLM prompt and gets multiple LLM responses with tool calls enabled.
 *
 * @param name Optional node name.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMRequestMultiple(name: String? = null): AIAgentNodeDelegateBase<String, List<Message.Response>> =
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
public fun <T> AIAgentSubgraphBuilderBase<*, *>.nodeLLMCompressHistory(
    name: String? = null,
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
): AIAgentNodeDelegateBase<T, T> = node(name) { input ->
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): AIAgentNodeDelegateBase<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        environment.executeTool(toolCall)
    }

/**
 * A node that adds a tool result to the prompt and requests an LLM response.
 *
 * @param name Optional node name.
 */
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendToolResult(
    name: String? = null
): AIAgentNodeDelegateBase<ReceivedToolResult, Message.Response> =
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeExecuteMultipleTools(
    name: String? = null,
    parallelTools: Boolean = false,
): AIAgentNodeDelegateBase<List<Message.Tool.Call>, List<ReceivedToolResult>> =
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
public fun AIAgentSubgraphBuilderBase<*, *>.nodeLLMSendMultipleToolResults(
    name: String? = null
): AIAgentNodeDelegateBase<List<ReceivedToolResult>, List<Message.Response>> =
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
public inline fun <reified ToolArg : ToolArgs, reified TResult : ToolResult> AIAgentSubgraphBuilderBase<*, *>.nodeExecuteSingleTool(
    name: String? = null,
    tool: Tool<ToolArg, TResult>,
    doUpdatePrompt: Boolean = true
): AIAgentNodeDelegateBase<ToolArg, SafeTool.Result<TResult>> =
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


/**
 * Represents the result of a retry operation, encapsulating the output of the operation
 * and a flag indicating whether the operation was successful.
 *
 * @property output The output of the retry operation.
 * @property isSuccess A boolean value indicating whether the success condition is satisfied.
 */
public data class RetryResult<Output>(val output: Output, val isSuccess: Boolean)


/**
 * Creates a node that retries execution of another node until it succeeds or reaches the maximum number of retries.
 * If none of the retries were unsuccessful, returns the output of the first execution.
 * If all retries failed with an exception, throws an exception.
 *
 * @param node The node to retry
 * @param maxRetries Maximum number of retry attempts (must be greater than 0)
 * @param successCondition Function that determines if the output is considered successful
 * @return A node that returns a [RetryResult] containing the output of the node and the success status
 *
 * Example usage:
 * ```
 * val nodeCallLLM by nodeLLMRequest("sendInput")
 * val nodeRetryCallLLM by nodeRetry(nodeCallLLM) { it is Message.Tool.Call }
 * val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
 *
 * // Forward successful results to the appropriate node based on the message type
 * edge(nodeRetryCallLLM forwardTo nodeExecuteTool transformed { it.output } onToolCall { true })
 * edge(nodeRetryCallLLM forwardTo nodeFinish transformed { it.output } onAssistantMessage { true })
 * ```
 */
public fun <Input, Output> AIAgentSubgraphBuilderBase<*, *>.nodeRetry(
    node: AIAgentNodeBase<Input, Output>,
    maxRetries: Int = 3,
    successCondition: (Output) -> Boolean,
): AIAgentNodeDelegateBase<Input, RetryResult<Output>> {
    require(maxRetries > 0) { "maxRetries must be greater than 0" }
    return node("retry_${node.name}") execute@{ input ->
        (1..maxRetries).mapNotNull {
            try {
                val newContext = fork()
                val output = node.execute(newContext, input)
                if (successCondition(output)) {
                    replace(newContext)
                    return@execute RetryResult(output, true)
                } else {
                    RetryResult(output, false)
                }
            } catch (_: Exception) { null }
        }.firstOrNull() ?: throw Error("Max retries limit reached, all retries failed with an exception.")
    }
}


/**
 * Creates a node that retries execution of another node until it succeeds or reaches the maximum number of retries.
 * If none of the retries were successful, throws an exception.
 *
 * @param node The node to retry
 * @param maxRetries Maximum number of retry attempts (must be greater than 0)
 * @param successCondition Function that determines if the output is considered successful
 * @return A node that returns a [Output] containing the successful result
 *
 * Example usage:
 * ```
 * val nodeCallLLM by nodeLLMRequest("sendInput")
 * val nodeRetryCallLLM by nodeRetryStrict(nodeCallLLM) { it is Message.Tool.Call }
 * val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
 *
 * // Forward to the appropriate node based on the message type
 * edge(nodeRetryCallLLM forwardTo nodeExecuteTool onToolCall { true })
 * // no need for other branches as the strict retry component will allow only specified output
 * ```
 */
public fun <Input, Output> AIAgentSubgraphBuilderBase<*, *>.nodeRetryStrict(
    node: AIAgentNodeBase<Input, Output>,
    maxRetries: Int = 3,
    successCondition: (Output) -> Boolean,
): AIAgentNodeDelegateBase<Input, Output> {
    val retryNode by nodeRetry(node, maxRetries) { successCondition(it) }
    return node("retryStrict_${node.name}") { input ->
        retryNode.execute(this, input).let {
            if (it.isSuccess) it.output else throw Error("Max retries limit reached, none of the retries succeeded.")
        }
    }
}
