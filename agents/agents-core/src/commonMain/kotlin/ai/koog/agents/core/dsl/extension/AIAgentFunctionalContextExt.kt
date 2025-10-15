package ai.koog.agents.core.dsl.extension

import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.executeTool
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredDataDefinition
import ai.koog.prompt.structure.StructuredResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.serializer

/**
 * Sends a message to a Large Language Model (LLM) and optionally allows the use of tools during the LLM interaction.
 * The message becomes part of the current prompt, and the LLM's response is processed accordingly,
 * either with or without tool integrations based on the provided parameters.
 *
 * @param message The content of the message to be sent to the LLM.
 * @param allowToolCalls Specifies whether tool calls are allowed during the LLM interaction. Defaults to `true`.
 */
public suspend fun AIAgentFunctionalContext.requestLLM(
    message: String,
    allowToolCalls: Boolean = true
): Message.Response {
    return llm.writeSession {
        updatePrompt {
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
 * Executes the provided action if the given response is of type [Message.Assistant].
 *
 * @param response The response message to evaluate, which may or may not be of type [Message.Assistant].
 * @param action A lambda function to execute if the response is an instance of [Message.Assistant].
 */
public inline fun AIAgentFunctionalContext.onAssistantMessage(
    response: Message.Response,
    action: (Message.Assistant) -> Unit
) {
    if (response is Message.Assistant) {
        action(response)
    }
}

/**
 * Checks if the list of `Message.Response` contains any instances
 * of `Message.Tool.Call`.
 *
 * @receiver A list of `Message.Response` objects to evaluate.
 * @return `true` if there is at least one `Message.Tool.Call` in the list, otherwise `false`.
 */
public fun List<Message.Response>.containsToolCalls(): Boolean = this.any { it is Message.Tool.Call }

/**
 * Attempts to cast a `Message.Response` instance to a `Message.Assistant` type.
 *
 * This method checks if the first element in the response is of type `Message.Assistant`
 * and, if so, returns it; otherwise, it returns `null`.
 *
 * @return The `Message.Assistant` instance if the cast is successful, or `null` if the cast fails.
 */
public fun Message.Response.asAssistantMessageOrNull(): Message.Assistant? = this as? Message.Assistant

/**
 * Casts the current instance of a [Message.Response] to a [Message.Assistant].
 * This function should only be used when it is guaranteed that the instance
 * is of type [Message.Assistant], as it will throw an exception if the type
 * does not match.
 *
 * @return The current instance cast to [Message.Assistant].
 */
public fun Message.Response.asAssistantMessage(): Message.Assistant = this as Message.Assistant

/**
 * Invokes the provided action when multiple tool call messages are found within a given list of response messages.
 * Filters the list of responses to include only instances of `Message.Tool.Call` and executes the action on the filtered list if it is not empty.
 *
 * @param response A list of response messages to be checked for tool call messages.
 * @param action A lambda function to be executed with the list of filtered tool call messages, if any exist.
 */
public inline fun AIAgentFunctionalContext.onMultipleToolCalls(
    response: List<Message.Response>,
    action: (List<Message.Tool.Call>) -> Unit
) {
    response.filterIsInstance<Message.Tool.Call>().takeIf { it.isNotEmpty() }?.let {
        action(it)
    }
}

/**
 * Extracts a list of tool call messages from a given list of response messages.
 *
 * @param response A list of response messages to filter, potentially containing various types of responses.
 * @return A list of messages specifically representing tool calls, which are instances of [Message.Tool.Call].
 */
public fun AIAgentFunctionalContext.extractToolCalls(
    response: List<Message.Response>
): List<Message.Tool.Call> = response.filterIsInstance<Message.Tool.Call>()

/**
 * Filters the provided list of response messages to include only assistant messages and,
 * if the filtered list is not empty, performs the specified action with the filtered list.
 *
 * @param response A list of response messages to be processed. Only those of type `Message.Assistant` will be considered.
 * @param action A lambda function to execute on the list of assistant messages if the filtered list is not empty.
 */
public inline fun AIAgentFunctionalContext.onMultipleAssistantMessages(
    response: List<Message.Response>,
    action: (List<Message.Assistant>) -> Unit
) {
    response.filterIsInstance<Message.Assistant>().takeIf { it.isNotEmpty() }?.let {
        action(it)
    }
}

/**
 * Retrieves the latest token usage from the prompt within the LLM session.
 *
 * @return The latest token usage information as an integer.
 */
public suspend fun AIAgentFunctionalContext.latestTokenUsage(): Int {
    return llm.readSession { prompt.latestTokenUsage }
}

/**
 * Sends a message to a Large Language Model (LLM) and requests structured data from the LLM with error correction capabilities.
 * The message becomes part of the current prompt, and the LLM's response is processed to extract structured data.
 *
 * @param message The content of the message to be sent to the LLM.
 * @param structure Definition of expected output format and parsing logic.
 * @param retries Number of retry attempts for failed generations.
 * @param fixingModel LLM used for error correction.
 * @return Result containing the structured response if successful, or an error if parsing failed.
 */
public suspend inline fun <reified T> AIAgentFunctionalContext.requestLLMStructured(
    message: String,
    examples: List<T> = emptyList(),
    fixingParser: StructureFixingParser? = null
): Result<StructuredResponse<T>> {
    return llm.writeSession {
        updatePrompt {
            user(message)
        }

        requestLLMStructured(
            serializer<T>(),
            examples,
            fixingParser
        )
    }
}

/**
 * Sends a message to a Large Language Model (LLM) and streams the LLM response.
 * The message becomes part of the current prompt, and the LLM's response is streamed as it's generated.
 *
 * @param message The content of the message to be sent to the LLM.
 * @param structureDefinition Optional structure to guide the LLM response.
 * @return A flow of [StreamFrame] objects from the LLM response.
 */
public suspend fun AIAgentFunctionalContext.requestLLMStreaming(
    message: String,
    structureDefinition: StructuredDataDefinition? = null
): Flow<StreamFrame> {
    return llm.writeSession {
        updatePrompt {
            user(message)
        }

        requestLLMStreaming(structureDefinition)
    }
}

/**
 * Sends a message to a Large Language Model (LLM) and gets multiple LLM responses with tool calls enabled.
 * The message becomes part of the current prompt, and multiple responses from the LLM are collected.
 *
 * @param message The content of the message to be sent to the LLM.
 * @return A list of LLM responses.
 */
public suspend fun AIAgentFunctionalContext.requestLLMMultiple(message: String): List<Message.Response> {
    return llm.writeSession {
        updatePrompt {
            user(message)
        }

        requestLLMMultiple()
    }
}

/**
 * Sends a message to a Large Language Model (LLM) that will only call tools without generating text responses.
 * The message becomes part of the current prompt, and the LLM is instructed to only use tools.
 *
 * @param message The content of the message to be sent to the LLM.
 * @return The LLM response containing tool calls.
 */
public suspend fun AIAgentFunctionalContext.requestLLMOnlyCallingTools(message: String): Message.Response {
    return llm.writeSession {
        updatePrompt {
            user(message)
        }

        requestLLMOnlyCallingTools()
    }
}

/**
 * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
 * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
 *
 * @param message The content of the message to be sent to the LLM.
 * @param tool The tool descriptor that the LLM must use.
 * @return The LLM response containing the tool call.
 */
public suspend fun AIAgentFunctionalContext.requestLLMForceOneTool(
    message: String,
    tool: ToolDescriptor
): Message.Response {
    return llm.writeSession {
        updatePrompt {
            user(message)
        }

        requestLLMForceOneTool(tool)
    }
}

/**
 * Sends a message to a Large Language Model (LLM) and forces it to use a specific tool.
 * The message becomes part of the current prompt, and the LLM is instructed to use only the specified tool.
 *
 * @param message The content of the message to be sent to the LLM.
 * @param tool The tool that the LLM must use.
 * @return The LLM response containing the tool call.
 */
public suspend fun AIAgentFunctionalContext.requestLLMForceOneTool(
    message: String,
    tool: Tool<*, *>
): Message.Response {
    return llm.writeSession {
        updatePrompt {
            user(message)
        }

        requestLLMForceOneTool(tool)
    }
}

/**
 * Executes a tool call and returns the result.
 *
 * @param toolCall The tool call to execute.
 * @return The result of the tool execution.
 */
public suspend fun AIAgentFunctionalContext.executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
    return environment.executeTool(toolCall)
}

/**
 * Executes multiple tool calls and returns their results.
 * These calls can optionally be executed in parallel.
 *
 * @param toolCalls The list of tool calls to execute.
 * @param parallelTools Specifies whether tools should be executed in parallel, defaults to false.
 * @return A list of results from the executed tool calls.
 */
public suspend fun AIAgentFunctionalContext.executeMultipleTools(
    toolCalls: List<Message.Tool.Call>,
    parallelTools: Boolean = false
): List<ReceivedToolResult> {
    return if (parallelTools) {
        environment.executeTools(toolCalls)
    } else {
        toolCalls.map { environment.executeTool(it) }
    }
}

/**
 * Adds a tool result to the prompt and requests an LLM response.
 *
 * @param toolResult The tool result to add to the prompt.
 * @return The LLM response.
 */
public suspend fun AIAgentFunctionalContext.sendToolResult(toolResult: ReceivedToolResult): Message.Response {
    return llm.writeSession {
        updatePrompt {
            tool {
                result(toolResult)
            }
        }

        requestLLM()
    }
}

/**
 * Adds multiple tool results to the prompt and gets multiple LLM responses.
 *
 * @param results The list of tool results to add to the prompt.
 * @return A list of LLM responses.
 */
public suspend fun AIAgentFunctionalContext.sendMultipleToolResults(
    results: List<ReceivedToolResult>
): List<Message.Response> {
    return llm.writeSession {
        updatePrompt {
            tool {
                results.forEach { result(it) }
            }
        }

        requestLLMMultiple()
    }
}

/**
 * Calls a specific tool directly using the provided arguments.
 *
 * @param tool The tool to execute.
 * @param toolArgs The arguments to pass to the tool.
 * @param doUpdatePrompt Specifies whether to add tool call details to the prompt.
 * @return The result of the tool execution.
 */
public suspend inline fun <reified ToolArg : ToolArgs, reified TResult : ToolResult> AIAgentFunctionalContext.executeSingleTool(
    tool: Tool<ToolArg, TResult>,
    toolArgs: ToolArg,
    doUpdatePrompt: Boolean = true
): SafeTool.Result<TResult> {
    return llm.writeSession {
        if (doUpdatePrompt) {
            updatePrompt {
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
 * Compresses the current LLM prompt (message history) into a summary, replacing messages with a TLDR.
 *
 * @param input The input value that will be returned unchanged after compression.
 * @param strategy Determines which messages to include in compression.
 * @param preserveMemory Specifies whether to retain message memory after compression.
 * @return The input value, unchanged.
 */
public suspend fun AIAgentFunctionalContext.compressHistory(
    strategy: HistoryCompressionStrategy = HistoryCompressionStrategy.WholeHistory,
    preserveMemory: Boolean = true
) {
    llm.writeSession {
        replaceHistoryWithTLDR(strategy, preserveMemory)
    }
}
