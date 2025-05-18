package ai.koog.agents.core.feature.handler

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolResult

public class ExecuteToolHandler {
    public var toolCallHandler: ToolCallHandler =
        ToolCallHandler { _, _ -> }

    public var toolValidationErrorHandler: ToolValidationErrorHandler =
        ToolValidationErrorHandler { _, _, _ -> }

    public var toolCallFailureHandler: ToolCallFailureHandler =
        ToolCallFailureHandler { _, _, _ -> }

    public var toolCallResultHandler: ToolCallResultHandler =
        ToolCallResultHandler { _, _, _ -> }
}

public fun interface ToolCallHandler {
    public suspend fun handle(tool: Tool<*, *>, toolArgs: Tool.Args)
}

public fun interface ToolValidationErrorHandler {
    public suspend fun handle(tool: Tool<*, *>, toolArgs: Tool.Args, error: String)
}

public fun interface ToolCallFailureHandler {
    public suspend fun handle(tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable)
}

public fun interface ToolCallResultHandler {
    public suspend fun handle(tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?)
}
