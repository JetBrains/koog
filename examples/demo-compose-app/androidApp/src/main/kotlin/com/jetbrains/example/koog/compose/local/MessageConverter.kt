package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.message.ContentPart
import com.google.ai.edge.litertlm.Message as LitertMessage
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.ai.edge.litertlm.Content
import kotlinx.datetime.Clock

fun convertLitertToKoogMessage(message: LitertMessage, clock: Clock): Message {
    val parts = message.contents.contents.map {
        when (it) {
            is Content.Text -> ContentPart.Text(it.text)
            else -> TODO("Not yet supported")
        }
    }
    return Message.Assistant(
        parts = parts,
        metaInfo = ResponseMetaInfo.create(clock),
    )
}

fun convertKoogToLitertMessage(message: Message): LitertMessage {
    return when (message.role) {
        // TODO: Ask for contents constructor
        Message.Role.System -> LitertMessage.of(message.content)
        Message.Role.User -> LitertMessage.of(message.content)
        Message.Role.Assistant -> LitertMessage.of(message.content)
        // TODO: Ask how to dump tools
        Message.Role.Tool -> LitertMessage.of(message.content)
        Message.Role.Reasoning -> TODO()
    }
}
