package ai.grazie.code.agents.core.feature.handler

import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.tools.ToolResult
import ai.grazie.code.agents.core.tools.tools.ToolStage
import ai.jetbrains.code.prompt.message.Message

class ExecuteToolHandler {
    var toolCallHandler: ToolCallHandler =
        ToolCallHandler { _, _, _ -> }

    var toolValidationErrorHandler: ToolValidationErrorHandler =
        ToolValidationErrorHandler { _, _, _, _ -> }

    var toolCallFailureHandler: ToolCallFailureHandler =
        ToolCallFailureHandler { _, _, _, _ -> }

    var toolCallResultHandler: ToolCallResultHandler =
        ToolCallResultHandler { _, _, _, _ -> }
}

fun interface ToolCallHandler {
    suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args)
}

fun interface ToolValidationErrorHandler {
    suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, error: String)
}

fun interface ToolCallFailureHandler {
    suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, throwable: Throwable)
}

fun interface ToolCallResultHandler {
    suspend fun handle(stage: ToolStage, tool: Tool<*, *>, toolArgs: Tool.Args, result: ToolResult?)
}