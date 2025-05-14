package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage

public class ExecuteToolHandler {
    public var toolCallHandler: ToolCallHandler =
        ToolCallHandler { _, _, _ -> }

    public var toolValidationErrorHandler: ToolValidationErrorHandler =
        ToolValidationErrorHandler { _, _, _, _ -> }

    public var toolCallFailureHandler: ToolCallFailureHandler =
        ToolCallFailureHandler { _, _, _, _ -> }

    public var toolCallResultHandler: ToolCallResultHandler =
        ToolCallResultHandler { _, _, _, _ -> }
}

public fun interface ToolCallHandler {
    public suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args)
}

public fun interface ToolValidationErrorHandler {
    public suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, error: String)
}

public fun interface ToolCallFailureHandler {
    public suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable)
}

public fun interface ToolCallResultHandler {
    public suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?)
}
