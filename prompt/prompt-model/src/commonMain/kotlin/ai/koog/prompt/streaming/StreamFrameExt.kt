package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message

/**
 * Convert a [Message.Response] to a [StreamFrame].
 */
public fun Message.Response.toStreamFrame(): StreamFrame =
    when(this) {
        is Message.Assistant -> StreamFrame.Append(content)
        is Message.Tool.Call -> StreamFrame.ToolCall(id, tool, content)
    }
