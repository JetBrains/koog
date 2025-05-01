package ai.grazie.code.agents.local.features.handler

import ai.grazie.code.agents.local.environment.ReceivedToolResult
import ai.jetbrains.code.prompt.message.Message


class ExecuteToolHandler {
    var beforeToolCallsHandler: BeforeToolCallsHandler =
        BeforeToolCallsHandler { _ -> }

    var afterToolCallsHandler: AfterToolCallsHandler =
        AfterToolCallsHandler { _ -> }
}

fun interface BeforeToolCallsHandler {
    suspend fun handle(tools: List<Message.Tool.Call>)
}

fun interface AfterToolCallsHandler {
    suspend fun handle(results: List<ReceivedToolResult>)
}
