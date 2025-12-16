package ai.koog.agents.features.acp

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import com.agentclientprotocol.common.Event.SessionUpdateEvent
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.SessionUpdate.AgentMessageChunk
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus
import kotlinx.datetime.Clock

private const val UNKNOWN_FORMAT = "unknown"
private const val UNKNOWN_MIME_TYPE = "unknown/unknown"
private const val UNKNOWN_URI = "unknown"
private const val UNKNOWN_FILE_NAME = "unknown"
private const val UNKNOWN_TOOL_CALL_ID = "unknown"

private fun parseFormat(mimeType: String?): String {
    return mimeType?.split("/")?.lastOrNull() ?: UNKNOWN_FORMAT
}

/**
 * Converts a list of [ContentBlock] of ACP prompt to a Koog [Message.User].
 */
public fun List<ContentBlock>.toKoogMessage(clock: Clock): Message {
    return Message.User(
        parts = this.map { it.toKoogContentPart() },
        metaInfo = RequestMetaInfo(clock.now())
    )
}

/**
 * Converts a single [ContentBlock] of ACP prompt to a Koog [ContentPart].
 */
public fun ContentBlock.toKoogContentPart(): ContentPart {
    return when (this) {
        // https://agentclientprotocol.com/protocol/content#audio-content
        is ContentBlock.Audio -> {
            ContentPart.Audio(
                content = AttachmentContent.Binary.Base64(data),
                format = parseFormat(mimeType),
                mimeType = mimeType
            )
        }

        // https://agentclientprotocol.com/protocol/content#image-content
        is ContentBlock.Image -> {
            ContentPart.Image(
                content = AttachmentContent.Binary.Base64(data),
                format = parseFormat(mimeType),
                mimeType = mimeType
            )
        }

        // https://agentclientprotocol.com/protocol/content#embedded-resource
        is ContentBlock.Resource -> {
            when (val resource = this.resource) {
                is EmbeddedResourceResource.BlobResourceContents -> {
                    ContentPart.File(
                        content = AttachmentContent.Binary.Base64(resource.blob),
                        format = parseFormat(resource.mimeType),
                        mimeType = resource.mimeType ?: UNKNOWN_MIME_TYPE
                    )
                }

                is EmbeddedResourceResource.TextResourceContents -> {
                    ContentPart.File(
                        content = AttachmentContent.PlainText(resource.text),
                        format = parseFormat(resource.mimeType),
                        mimeType = resource.mimeType ?: UNKNOWN_MIME_TYPE
                    )
                }
            }
        }

        // https://agentclientprotocol.com/protocol/content#resource-link
        is ContentBlock.ResourceLink -> {
            ContentPart.File(
                content = AttachmentContent.URL(uri),
                format = parseFormat(mimeType),
                mimeType = mimeType ?: UNKNOWN_MIME_TYPE
            )
        }

        // https://agentclientprotocol.com/protocol/content#text-content
        is ContentBlock.Text -> {
            ContentPart.Text(text)
        }
    }
}

/**
 * Converts a [Message.Response] to a list of ACP [SessionUpdateEvent].
 */
public fun Message.Response.toAcpEvents(): List<SessionUpdateEvent> {
    val response = this
    return buildList {
        when (response) {
            is Message.Assistant -> {
                response.parts.forEach { part ->
                    add(
                        SessionUpdateEvent(
                            update = AgentMessageChunk(part.toAcpContentBlock())
                        )
                    )
                }
            }

            is Message.Reasoning -> {
                add(
                    SessionUpdateEvent(
                        update = SessionUpdate.AgentThoughtChunk(
                            content = ContentBlock.Text(response.content)
                        )
                    )
                )
            }

            is Message.Tool.Call -> {
                add(
                    SessionUpdateEvent(
                        update = SessionUpdate.ToolCall(
                            toolCallId = ToolCallId(response.id ?: UNKNOWN_TOOL_CALL_ID),
                            // TODO: Support tool description in the event
                            title = response.tool,
                            // TODO: Support kind for tools
                            status = ToolCallStatus.PENDING,
                            rawInput = response.contentJson,
                        )
                    )
                )
            }
        }
    }
}

/**
 * Converts a ContentPart to an ACP ContentBlock.
 */
public fun ContentPart.toAcpContentBlock(): ContentBlock {
    return when (this) {
        is ContentPart.Text -> {
            ContentBlock.Text(this.text)
        }

        is ContentPart.Audio -> {
            ContentBlock.Audio(
                data = this.content.toString(),
                mimeType = this.mimeType,
            )
        }

        is ContentPart.File ->
            when (val content = this.content) {
                is AttachmentContent.Binary.Base64 -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.BlobResourceContents(
                        blob = content.base64,
                        // TODO: add uri to the file
                        uri = UNKNOWN_URI,
                        mimeType = this.mimeType
                    )
                )

                is AttachmentContent.Binary.Bytes -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.BlobResourceContents(
                        blob = content.asBase64(),
                        // TODO: add uri to the file
                        uri = UNKNOWN_URI,
                        mimeType = this.mimeType
                    )
                )

                is AttachmentContent.PlainText -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.TextResourceContents(
                        text = content.text,
                        // TODO: add uri to the file
                        uri = UNKNOWN_URI
                    )
                )

                is AttachmentContent.URL -> {
                    ContentBlock.ResourceLink(
                        name = this.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url
                    )
                }
            }

        is ContentPart.Image -> {
            ContentBlock.Image(
                data = this.content.toString(),
                mimeType = this.mimeType,
            )
        }

        is ContentPart.Video -> {
            throw AcpException("Video content is not supported yet in Acp content blocks.")
        }
    }
}
