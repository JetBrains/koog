package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.serialization.JSONSerializer

/**
 * Helper function for [SafeTool] that is created from [ToolFromCallable].
 * Allows a simpler execution by passing the [ToolFromCallable.callable] positional arguments directly as [args]
 * instead of creating [ToolFromCallable.Args] manually.
 *
 * It is unsafe since it doesn't validate types of the [args], so it can throw type mismatch related exceptions.
 *
 * Example:
 * ```kotlin
 * // Sample tool function
 * fun myTool(a: String, b: Int): String = "$a=$b"
 *
 * // Environment-aware safe tool instance
 * val safeTool = SafeTool(::myTool.asTool(), environment, clock)
 *
 * // Can trigger tool execution manually without creating Args object by hand
 * val result = safeTool.executeUnsafe(serializer, "test", 123)
 * ```
 */
public suspend fun <TResult> SafeTool<ToolFromCallable.Args, TResult>.executeUnsafe(
    serializer: JSONSerializer,
    vararg args: Any?,
): SafeTool.Result<TResult> {
    val toolFunction = (tool as? ToolFromCallable<TResult>)?.callable
        ?: throw IllegalArgumentException("Tool must be ToolFromCallable")

    val params = toolFunction.parameters

    check(args.size == params.size) {
        "Number of arguments provided must match the the number " +
            "of parameters in the tool function:" +
            " ${toolFunction.name} ${params.size} != ${args.size}"
    }

    val argsMap = (params zip args).associate { (param, arg) ->
        param to arg
    }

    return execute(ToolFromCallable.Args(argsMap), serializer)
}
