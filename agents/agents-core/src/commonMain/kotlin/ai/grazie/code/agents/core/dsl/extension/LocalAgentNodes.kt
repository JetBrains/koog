package ai.grazie.code.agents.core.dsl.extension

import ai.grazie.code.agents.core.dsl.builder.LocalAgentNodeDelegate
import ai.grazie.code.agents.core.dsl.builder.LocalAgentNodeDelegateBase
import ai.grazie.code.agents.core.dsl.builder.LocalAgentSubgraphBuilderBase
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.environment.SafeTool
import ai.grazie.code.agents.core.environment.executeTool
import ai.grazie.code.agents.core.environment.result
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolDescriptor
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.prompt.structure.StructuredData
import ai.grazie.code.prompt.structure.StructuredDataDefinition
import ai.grazie.code.prompt.structure.StructuredResponse
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.flow.Flow

/**
 * A node in that performs no actions. The input is directly passed as the output without any processing.
 *
 * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
 * @return A delegate for the created node, representing a no-operation transformation where the input is returned as output.
 */
public fun <T> LocalAgentSubgraphBuilderBase<*, *>.nodeDoNothing(name: String? = null): LocalAgentNodeDelegateBase<T, T> =
    node(name) { input -> input }

// ================
// Simple LLM nodes
// ================

/**
 * A node that just updates the prompt without asking the LLM
 *
 * @param name An optional name for the node. If not provided, the name will default to the delegate's property name.
 * @param body A lambda block specifying the logic to update the prompt using the [ai.jetbrains.code.prompt.dsl.PromptBuilder].
 * @return A delegate that represents the created node, which takes no input and produces no output.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeUpdatePrompt(
    name: String? = null,
    body: PromptBuilder.() -> Unit
): LocalAgentNodeDelegateBase<Unit, Unit> =
    node(name) {
        llm.writeSession {
            updatePrompt {
                body()
            }
        }
    }

/**
 * LLM node that updates the prompt with the user's stage input and triggers an LLM request within a write session.
 *
 * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
 * @return A delegate representing the defined node, which takes no input (Unit) and produces a `Message.Response` from the LLM.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendStageInput(name: String? = null): LocalAgentNodeDelegateBase<Unit, Message.Response> =
    node(name) { _ ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
            }

            requestLLM()
        }
    }

/**
 * Creates a node that sends the current stage input to the LLM and gets multiple responses.
 *
 * @param name Optional name for the node.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendStageInputMultiple(
    name: String? = null
): LocalAgentNodeDelegateBase<Unit, List<Message.Response>> =
    node(name) { _ ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
            }

            requestLLMMultiple()
        }
    }


/**
 * Creates a node that sends a user message to the LLM and gets a response with LLM allowed ONLY to call tools.
 *
 * @param name Optional name for the node.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageOnlyCallingTools(name: String? = null): LocalAgentNodeDelegateBase<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMOnlyCallingTools()
        }
    }

/**
 * Creates a node that sends a user message to the LLM and gets a response,
 * with LLM forced to call specifically the provided tool.
 *
 * @param name Optional name for the node.
 * @param tool Tool that LLM is forced to call.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: ToolDescriptor
): LocalAgentNodeDelegateBase<String, Message.Response> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMForceOneTool(tool)
        }
    }

/**
 * Creates a node that sends a user message to the LLM and gets a response,
 * with LLM forced to call specifically the provided tool.
 *
 * @param name Optional name for the node.
 * @param tool Tool that LLM is forced to call.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendMessageForceOneTool(
    name: String? = null,
    tool: Tool<*, *>
): LocalAgentNodeDelegateBase<String, Message.Response> =
    nodeLLMSendMessageForceOneTool(name, tool.descriptor)

/**
 * LLM node that processes user messages and returns a response from LLM. The node configuration determines whether tool
 * calls are allowed during the processing of the message.
 *
 * @param name An optional name for the node. If not provided, the name will default to the
 * property name of the delegate.
 * @param allowToolCalls A flag indicating whether tool calls are permitted during
 * the execution of the LLM process. Defaults to `true`.
 * @return A `LocalAgentNodeDelegate` that delegates the execution of an LLM call,
 * processing an input message and returning a `Message.Response`.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequest(
    name: String? = null,
    allowToolCalls: Boolean = true
): LocalAgentNodeDelegateBase<String, Message.Response> =
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
 * Defines a node that sends a structured data request to an LLM (Language Model) to generate a structured response.
 * The response is based on the specified structure, with retries and error correction capabilities.
 *
 * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
 * @param structure The structured data definition specifying the expected structured output format, schema, and parsing logic.
 * @param retries The number of retry attempts to allow in case of generation failures.
 * @param fixingModel The language model to use for re-parsing or error correction during retries.
 * @return A `LocalAgentNodeDelegate` that produces a structured response containing both the parsed structure and the raw response text.
 */
public fun <T> LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStructured(
    name: String? = null,
    structure: StructuredData<T>,
    retries: Int,
    fixingModel: LLModel
): LocalAgentNodeDelegateBase<String, Result<StructuredResponse<T>>> =
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
 * Creates a node within the subgraph that streams data from an LLM (Language Learning Model) request,
 * processes the streamed data using a transformation function, and outputs the resulting flow.
 *
 * @param name Optional name for the node. If not provided, the delegate's property name will be used.
 * @param structureDefinition Optional structured data definition which provides additional context for the LLM request.
 *                             When provided, this definition is incorporated into the LLM request's prompt.
 * @param transformStreamData A suspendable transformation function that processes a flow of strings
 *                            obtained from the LLM request and returns a new flow of transformed data.
 * @return A delegate for the created node, which can be used to include it in the subgraph.
 */
public fun <T> LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructuredDataDefinition? = null,
    transformStreamData: suspend (Flow<String>) -> Flow<T>
): LocalAgentNodeDelegateBase<String, Flow<T>> =
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
 * Adds a node to the agent's subgraph that executes an LLM request and streams the output.
 *
 * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
 * @param structureDefinition An optional definition to structure the LLM request data.
 * @return A delegate representing the node, where the input is a String message and the output is a Flow of String representing the streamed LLM responses.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequestStreaming(
    name: String? = null,
    structureDefinition: StructuredDataDefinition? = null,
): LocalAgentNodeDelegateBase<String, Flow<String>> = nodeLLMRequestStreaming(name, structureDefinition) { it }

/**
 * LLM node that sends a user message to the LLM and gets a response with tools enabled,
 * potentially receiving multiple tool calls.
 *
 * @param name Optional name for the node.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequestMultiple(name: String? = null): LocalAgentNodeDelegateBase<String, List<Message.Response>> =
    node(name) { message ->
        llm.writeSession {
            updatePrompt {
                user(message)
            }

            requestLLMMultiple()
        }
    }

/**
 * LLM node that rewrites message history, leaving only user message and resulting TLDR.
 *
 * @param fromLastN Number of last messages used as a context for TLDR.
 * Default is `null`, which means entire history will be used.
 */
public fun <T> LocalAgentSubgraphBuilderBase<*, *>.nodeLLMCompressHistory(
    name: String? = null,
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
): LocalAgentNodeDelegateBase<T, T> = node(name) { input ->
    llm.writeSession {
        replaceHistoryWithTLDR(strategy, preserveMemory)
    }

    input
}

// ==========
// Tool nodes
// ==========

/**
 * A node that executes a single tool call and returns its result.
 *
 * @param name Optional name for the node.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): LocalAgentNodeDelegateBase<Message.Tool.Call, ReceivedToolResult> =
    node(name) { toolCall ->
        environment.executeTool(toolCall)
    }

/**
 * An LLM node in the that processes a `ToolCall.Result` and generates a `Message.Response`.
 * The tool result is incorporated into the prompt, and a request is made to the LLM for a response.
 *
 * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
 * @return A delegate representing the node, handling the transformation from `ToolCall.Result` to `Message.Response`.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendToolResult(
    name: String? = null
): LocalAgentNodeDelegateBase<ReceivedToolResult, Message.Response> =
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
 * A node that executes multiple tool calls and returns their results.
 *
 * @param name Optional name for the node.
 * @param parallelTools Should tools be called in environment in parallel (`false` by default)
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeExecuteMultipleTools(
    name: String? = null,
    parallelTools: Boolean = false,
): LocalAgentNodeDelegateBase<List<Message.Tool.Call>, List<ReceivedToolResult>> =
    node(name) { toolCalls ->
        if (parallelTools) {
            environment.executeTools(toolCalls)
        } else {
            toolCalls.map { environment.executeTool(it) }
        }
    }

/**
 * A node that sends multiple tool execution results to the LLM and gets multiple responses.
 *
 * @param name Optional name for the node.
 */
public fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendMultipleToolResults(
    name: String? = null
): LocalAgentNodeDelegateBase<List<ReceivedToolResult>, List<Message.Response>> =
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
 * Creates a node that calls a specific tool with passed arguments.
 *
 * @param name Optional name for the node.
 * @param tool The tool call to execute.
 */
public inline fun <reified ToolArg : Tool.Args, reified TResult : ToolResult> LocalAgentSubgraphBuilderBase<*, *>.nodeExecuteSingleTool(
    name: String? = null,
    tool: Tool<ToolArg, TResult>,
    doUpdatePrompt: Boolean = true
): LocalAgentNodeDelegateBase<ToolArg, SafeTool.Result<TResult>> =
    node(name) { toolArgs ->
        llm.writeSession {
            if (doUpdatePrompt) {
                updatePrompt {
                    // Why not tool message? Because it requires id != null to send it back to the LLM,
                    // The only workaround is to generate it
                    user(
                        "Tool call: ${tool.name} was explicitly called with args: ${
                            tool.encodeArgsToString(toolArgs)
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
