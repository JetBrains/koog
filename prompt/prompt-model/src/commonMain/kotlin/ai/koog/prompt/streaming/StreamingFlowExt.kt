package ai.koog.prompt.streaming

import ai.koog.prompt.message.Message

public fun Message.Response.toStreamingFrame(): StreamChunk =
    when(this) {
        is Message.Assistant -> StreamChunk.Append(content)
        is Message.Tool.Call -> StreamChunk.ToolCall(id, tool, content)
    }
