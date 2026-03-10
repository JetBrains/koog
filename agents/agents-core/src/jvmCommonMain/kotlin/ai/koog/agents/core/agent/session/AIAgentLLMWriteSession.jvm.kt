@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.agent.session

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.environment.SafeTool
import ai.koog.agents.core.environment.executeUnsafe
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.asTool
import kotlin.reflect.KFunction

/**
 * Finds a specific tool within the tool registry using the given tool function and returns it as a safe tool.
 *
 * @param toolFunction The reference to the function defining the tool to be located.
 * @return A safe representation of the tool associated with the provided function.
 * @throws IllegalArgumentException If the tool corresponding to the given function is not found in the tool registry.
 */
public inline fun <reified TResult> AIAgentLLMWriteSession.findTool(
    toolFunction: KFunction<TResult>
): SafeTool<ToolFromCallable.Args, TResult> {
    val toolFromCallable = toolFunction.asTool()

    val tool = toolRegistry.tools.filterIsInstance<ToolFromCallable<TResult>>()
        .find { it == toolFromCallable }
        ?: throw IllegalArgumentException("Tool with fromReference ${toolFunction.name} is not defined")

    return SafeTool(tool, environment, clock)
}

/**
 * Invokes a specified tool function within the AI Agent's write session context.
 *
 * @param toolFunction The tool function to be executed.
 * @param args The arguments to pass to the tool function.
 * @return The result of executing the specified tool function.
 * @throws IllegalArgumentException If the tool corresponding to the given function is not found in the tool registry.
 */
public suspend inline fun <reified TResult> AIAgentLLMWriteSession.callTool(
    toolFunction: KFunction<TResult>,
    vararg args: Any?
): SafeTool.Result<TResult> {
    return findTool(toolFunction)
        .executeUnsafe(config.serializer, args)
}
