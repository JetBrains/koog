package ai.koog.agents.core.agent.session

import ai.koog.agents.core.environment.SafeToolFromCallable
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.core.tools.reflect.asTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberProperties

/**
 * Finds a specific tool within the tool registry using the given tool function and returns it as a safe tool.
 *
 * @param toolFunction The reference to the function defining the tool to be located.
 * @return A safe representation of the tool associated with the provided function.
 * @throws IllegalArgumentException If the tool corresponding to the given function is not found in the tool registry.
 */
public inline fun <reified TResult> AIAgentLLMWriteSession.findTool(toolFunction: KFunction<TResult>): SafeToolFromCallable<TResult> {
    val toolFromCallable = toolFunction.asTool()

    toolRegistry.tools.filterIsInstance<ToolFromCallable>()
        .find { it.name == toolFromCallable.name }
        ?: throw IllegalArgumentException("Tool with fromReference ${toolFunction.name} is not defined")

    return SafeToolFromCallable(toolFunction, environment, clock)
}

/**
 * Invokes a specified tool function within the AI Agent's write session context.
 *
 * @param TResult The return type of the tool function being called.
 * @param toolFunction The tool function to be executed.
 * @param args The arguments to pass to the tool function.
 * @return The result of executing the specified tool function.
 */
public suspend inline fun <reified TResult> AIAgentLLMWriteSession.callTool(
    toolFunction: KFunction<TResult>,
    vararg args: Any?
): SafeToolFromCallable.Result<TResult> {
    return findTool(toolFunction).execute(args)
}

/**
 * Transforms a flow of argument data into a flow of parallel tool call results using the provided `SafeToolFromCallable`.
 *
 * This function executes the provided tool in parallel with a specified degree of concurrency,
 * mapping input argument data to tool parameters as necessary. Each result encapsulates the outcome
 * of the tool execution (success or failure).
 *
 * @param TArgsData The type of the input argument data emitted by the flow.
 * @param TResult The type of the resulting data produced by the tool calls.
 * @param safeTool An instance of `SafeToolFromCallable` wrapping the tool function to be executed.
 * @param concurrency An optional parameter specifying the maximum number of concurrent tool executions. Defaults to 16.
 * @return A flow emitting `SafeToolFromCallable.Result` representing the outcome of each tool execution.
 */
public inline fun <reified TArgsData, reified TResult> Flow<TArgsData>.toParallelToolCalls(
    safeTool: SafeToolFromCallable<TResult>,
    concurrency: Int = 16
): Flow<SafeToolFromCallable.Result<TResult>> = flatMapMerge(concurrency) { argsData ->
    flow {
        when {
            safeTool.toolFunction.parameters.size == 1 -> emit(safeTool.execute(argsData))
            else -> {
                val argsMap = mutableMapOf<Int, Any?>()
                safeTool.toolFunction.parameters.mapIndexed { index, param ->
                    val matchingProperty = argsData::class.memberProperties.find { it.name == param.name }
                        ?: error("No property found for ${param.name}")

                    argsMap[index] = matchingProperty.getter.call(argsData)
                }
                emit(safeTool.execute(*argsMap.values.toTypedArray()))
            }
        }
    }
}

/**
 * Transforms a flow of input arguments into a flow of raw parallel tool calls.
 *
 * This function takes a flow of input arguments and applies a tool execution function
 * in parallel with a specified level of concurrency. It executes the tool using its
 * raw execution method and emits the raw results as strings.
 *
 * @param TArgs The type of the input arguments for the tool calls.
 * @param TResult The type of the result produced by the tool function.
 * @param safeTool A wrapper for the tool capable of executing raw calls.
 * @param concurrency The level of concurrency to use for parallel processing. Defaults to 16.
 * @return A flow of raw string results obtained from the tool calls.
 */
public inline fun <reified TArgs, reified TResult> Flow<TArgs>.toParallelToolCallsRaw(
    safeTool: SafeToolFromCallable<TResult>,
    concurrency: Int = 16
): Flow<String> = flatMapMerge(concurrency) { args ->
    flow {
        emit(safeTool.executeRaw(args))
    }
}

/**
 * Executes a parallelized tool call using the provided data flow and tool function within the session.
 *
 * @param flow A Flow of input data arguments of type DataArgs to be processed.
 * @param foolFunction A KFunction representing the tool to invoke for processing inputs.
 * @param concurrency The maximum number of concurrent executions allowed for the tool calls. Defaults to 16.
 * @return A Flow of results wrapped as SafeToolFromCallable.Result containing the output of the tool calls.
 */
public inline fun <reified DataArgs, reified TResult> AIAgentLLMWriteSession.emitParallelToolCalls(
    flow: Flow<DataArgs>,
    foolFunction: KFunction<TResult>,
    concurrency: Int = 16
): Flow<SafeToolFromCallable.Result<TResult>> {
    val tool = findTool<TResult>(foolFunction)
    return flow.toParallelToolCalls(tool, concurrency)
}

/**
 * Executes parallel tool calls in a raw format using the provided flow of data arguments.
 *
 * @param flow The flow of data arguments that will be processed by the tool calls.
 * @param foolFunction The function representing the tool to be invoked for each data argument.
 * @param concurrency The maximum level of concurrency for the tool calls. Defaults to 16.
 * @return A flow that emits the raw string results from the tool calls.
 */
public inline fun <reified DataArgs, reified TResult> AIAgentLLMWriteSession.emitParallelToolCallsRaw(
    flow: Flow<DataArgs>,
    foolFunction: KFunction<TResult>,
    concurrency: Int = 16
): Flow<String> {
    val tool = findTool(foolFunction)
    return flow.toParallelToolCallsRaw(tool, concurrency)
}