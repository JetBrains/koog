@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.processor.ResponseProcessor
import kotlin.reflect.KClass
import kotlin.time.Clock

/**
 * A session for managing interactions with a language learning model (LLM)
 * and tools in an agent environment. This class provides functionality for executing
 * LLM requests, managing tools, and customizing prompts dynamically within a specific
 * session context.
 */
public expect class AIAgentLLMWriteSession
/**
 * Internal constructor used by the session infrastructure to create a mutable LLM session.
 *
 * @param environment The environment in which the AI agent operates.
 * @param executor The prompt executor used to send requests to the model.
 * @param tools A list of tool descriptors available in this session.
 * @param toolRegistry Registry containing all tools available for resolution by this session.
 * @param prompt The current prompt used as input for requests.
 * @param model The active model used to execute requests.
 * @param responseProcessor Optional response post-processor for model outputs.
 * @param config Agent configuration used by this session.
 * @param clock Clock used for timestamped prompt updates and related operations.
 */
internal constructor(
    environment: AIAgentEnvironment,
    executor: PromptExecutor,
    tools: List<ToolDescriptor>,
    toolRegistry: ToolRegistry,
    prompt: Prompt,
    model: LLModel,
    responseProcessor: ResponseProcessor?,
    config: AIAgentConfig,
    clock: Clock
) : AIAgentLLMWriteSessionCommon

/**
 * Executes the specified tool with the given arguments and returns the result within a [SafeTool.Result] wrapper.
 *
 * @param TArgs the type of arguments required by the tool.
 * @param TResult the type of result returned by the tool, implementing `ToolResult`.
 * @param tool the tool to be executed.
 * @param args the arguments required to execute the tool.
 * @return a `SafeTool.Result` containing the tool's execution result of type `TResult`.
 */
public suspend fun <TArgs, TResult> AIAgentLLMWriteSession.callTool(
    tool: Tool<TArgs, TResult>,
    args: TArgs
): SafeTool.Result<TResult> {
    return findTool(tool::class).execute(args, config.serializer)
}

/**
 * Executes a tool by its name with the provided arguments.
 *
 * @param toolName The name of the tool to be executed.
 * @param args The arguments required to execute the tool.
 * @return A [SafeTool.Result] containing the result of the tool execution, which is a subtype of [ai.koog.agents.core.tools.ToolResult].
 */
public suspend fun <TArgs> AIAgentLLMWriteSession.callTool(
    toolName: String,
    args: TArgs
): SafeTool.Result<out Any?> {
    return findToolByName<TArgs>(toolName).execute(args, config.serializer)
}

/**
 * Executes a tool identified by its name with the provided arguments and returns the raw string result.
 *
 * @param toolName The name of the tool to be executed.
 * @param args The arguments to be passed to the tool.
 * @return The raw result of the tool's execution as a String.
 */
public suspend fun <TArgs> AIAgentLLMWriteSession.callToolRaw(
    toolName: String,
    args: TArgs
): String {
    return findToolByName<TArgs>(toolName).execute(args, config.serializer).content
}

/**
 * Executes a tool operation based on the provided tool class and arguments.
 *
 * @param TArgs The type of arguments required by the tool.
 * @param TResult The type of result produced by the tool.
 * @param toolClass The class of the tool to be executed.
 * @param args The arguments to be passed to the tool for its execution.
 * @return A result wrapper containing either the successful result of the tool's execution or an error.
 */
public suspend fun <TArgs, TResult> AIAgentLLMWriteSession.callTool(
    toolClass: KClass<out Tool<TArgs, TResult>>,
    args: TArgs
): SafeTool.Result<TResult> {
    val tool = findTool(toolClass)
    return tool.execute(args, config.serializer)
}

/**
 * Invokes a tool of the specified type with the provided arguments.
 *
 * @param args The input arguments required for the tool execution.
 * @return A `SafeTool.Result` containing the outcome of the tool's execution, which may be of any type that extends `ToolResult`.
 */
public suspend inline fun <reified ToolT : Tool<Any?, Any?>> AIAgentLLMWriteSession.callTool(
    args: Any?
): SafeTool.Result<out Any?> {
    val tool = findTool(ToolT::class)
    return tool.executeUnsafe(args, config.serializer)
}

/**
 * Finds and retrieves a tool by its name and argument/result types.
 *
 * This function looks for a tool in the tool registry by its name and ensures that the tool
 * is compatible with the specified argument and result types. If no matching tool is found,
 * or if the specified types are incompatible, an exception is thrown.
 *
 * @param toolName the name of the tool to retrieve
 * @return the tool that matches the specified name and types
 * @throws IllegalArgumentException if the tool is not defined or the types are incompatible
 */
public fun <TArgs, TResult> AIAgentLLMWriteSession.findToolByNameAndArgs(
    toolName: String
): Tool<TArgs, TResult> =
    @Suppress("UNCHECKED_CAST")
    (
        toolRegistry.getTool(toolName) as? Tool<TArgs, TResult>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments")
        )

/**
 * Finds a tool by its name and ensures its arguments are compatible with the specified type.
 *
 * @param toolName The name of the tool to be retrieved.
 * @return A SafeTool instance wrapping the tool with the specified argument type.
 * @throws IllegalArgumentException If the tool with the specified name is not defined or its arguments
 * are incompatible with the expected type.
 */
public fun <TArgs> AIAgentLLMWriteSession.findToolByName(toolName: String): SafeTool<TArgs, *> {
    @Suppress("UNCHECKED_CAST")
    val tool = (
        toolRegistry.getTool(toolName) as? Tool<TArgs, *>
            ?: throw IllegalArgumentException("Tool \"$toolName\" is not defined or has incompatible arguments")
        )

    return SafeTool(tool, environment, clock)
}
