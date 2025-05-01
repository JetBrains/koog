package ai.grazie.code.agents.local.dsl.extensions

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.local.dsl.builders.LocalAgentNodeDelegate
import ai.grazie.code.agents.local.dsl.builders.LocalAgentSubgraphBuilderBase
import ai.grazie.code.agents.local.environment.ReceivedToolResult
import ai.grazie.code.agents.local.environment.SafeTool
import ai.grazie.code.agents.local.environment.executeTool
import ai.grazie.code.agents.local.environment.result
import ai.jetbrains.code.prompt.dsl.PromptBuilder
import ai.jetbrains.code.prompt.message.Message

/**
 * A node in that performs no actions. The input is directly passed as the output without any processing.
 *
 * @param name An optional name for the node. If not provided, the property name of the delegate will be used.
 * @return A delegate for the created node, representing a no-operation transformation where the input is returned as output.
 */
fun <T> LocalAgentSubgraphBuilderBase<*, *>.nodeDoNothing(name: String? = null): LocalAgentNodeDelegate<T, T> =
    node(name) { input -> input }

// ================
// Simple LLM nodes
// ================

/**
 * A node that just updates the prompt without asking the LLM
 *
 * @param name An optional name for the node. If not provided, the name will default to the delegate's property name.
 * @param body A lambda block specifying the logic to update the prompt using the [PromptBuilder].
 * @return A delegate that represents the created node, which takes no input and produces no output.
 */
fun LocalAgentSubgraphBuilderBase<*, *>.nodeUpdatePrompt(
    name: String? = null,
    body: PromptBuilder.() -> Unit
): LocalAgentNodeDelegate<Unit, Unit> =
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
fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendStageInput(name: String? = null): LocalAgentNodeDelegate<Unit, Message.Response> =
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
fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendStageInputMultiple(
    name: String? = null
): LocalAgentNodeDelegate<Unit, List<Message.Response>> =
    node(name) { _ ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
            }

            requestLLMMultiple()
        }
    }


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
fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequest(
    name: String? = null,
    allowToolCalls: Boolean = true
): LocalAgentNodeDelegate<String, Message.Response> =
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
 * LLM node that sends a user message to the LLM and gets a response with tools enabled,
 * potentially receiving multiple tool calls.
 *
 * @param name Optional name for the node.
 */
fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMRequestMultiple(name: String? = null): LocalAgentNodeDelegate<String, List<Message.Response>> =
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
fun <T> LocalAgentSubgraphBuilderBase<*, *>.nodeLLMCompressHistory(
    name: String? = null,
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
): LocalAgentNodeDelegate<T, T> = node(name) { input ->
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
fun LocalAgentSubgraphBuilderBase<*, *>.nodeExecuteTool(
    name: String? = null
): LocalAgentNodeDelegate<Message.Tool.Call, ReceivedToolResult> =
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
fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendToolResult(
    name: String? = null
): LocalAgentNodeDelegate<ReceivedToolResult, Message.Response> =
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
 */
fun LocalAgentSubgraphBuilderBase<*, *>.nodeExecuteMultipleTools(
    name: String? = null
): LocalAgentNodeDelegate<List<Message.Tool.Call>, List<ReceivedToolResult>> =
    node(name) { toolCalls ->
        environment.executeTools(toolCalls)
    }

/**
 * A node that sends multiple tool execution results to the LLM and gets multiple responses.
 *
 * @param name Optional name for the node.
 */
fun LocalAgentSubgraphBuilderBase<*, *>.nodeLLMSendMultipleToolResults(
    name: String? = null
): LocalAgentNodeDelegate<List<ReceivedToolResult>, List<Message.Response>> =
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
inline fun <reified ToolArg : Tool.Args, reified TResult : ToolResult> LocalAgentSubgraphBuilderBase<*, *>.nodeExecuteSingleTool(
    name: String? = null,
    tool: Tool<ToolArg, TResult>,
    doUpdatePrompt: Boolean = true
): LocalAgentNodeDelegate<ToolArg, SafeTool.Result<TResult>> =
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