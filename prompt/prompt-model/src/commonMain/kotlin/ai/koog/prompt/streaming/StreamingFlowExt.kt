package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message

public fun Message.Response.toStreamingFrame(): StreamingFrame =
    when(this) {
        is Message.Assistant -> StreamingFrame.Append(content)
        is Message.Tool.Call -> StreamingFrame.ToolCall(id, tool, content)
    }
