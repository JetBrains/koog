package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlinx.datetime.Clock
import com.google.ai.edge.litertlm.Message as LitertMessage

fun LitertMessage.toKoogMessages(clock: Clock): List<Message.Response> {
    return buildList {
        if (contents.contents.isNotEmpty()) {
            val parts = contents.contents.map {
                when (it) {
                    is Content.Text -> ContentPart.Text(it.text)
                    else -> TODO("Not yet supported")
                }
            }
            add(
                Message.Assistant(
                    parts = parts,
                    metaInfo = ResponseMetaInfo.create(clock),
                )
            )
        }

        if (toolCalls.isNotEmpty()) {
            toolCalls.forEach { toolCall ->
                add(
                    Message.Tool.Call(
                        id = null,
                        tool = toolCall.name,
                        content = toolCall.arguments.toJson().toString(),
                        metaInfo = ResponseMetaInfo.create(clock),
                    )
                )
            }
        }
    }
}

fun Message.toLitertMessage(): LitertMessage {
    return when (role) {
        Message.Role.System -> LitertMessage.system(content)
        Message.Role.User -> LitertMessage.user(content)
        Message.Role.Assistant -> LitertMessage.model(content)
        Message.Role.Tool -> LitertMessage.tool(Contents.of(content))
        Message.Role.Reasoning -> TODO()
    }
}
