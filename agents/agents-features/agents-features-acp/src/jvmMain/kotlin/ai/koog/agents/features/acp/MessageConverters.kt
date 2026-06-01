package ai.koog.agents.features.acp

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentContent.Binary.Base64
import ai.koog.prompt.message.AttachmentContent.PlainText
import ai.koog.prompt.message.AttachmentContent.URL
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.AttachmentSource.File
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.utils.time.KoogClock
import com.agentclientprotocol.common.Event.SessionUpdateEvent
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.SessionUpdate.AgentMessageChunk
import com.agentclientprotocol.model.ToolCallId
import com.agentclientprotocol.model.ToolCallStatus

/** Constant to use for an unknown content part format */
public const val UNKNOWN_FORMAT: String = "unknown"

/**  Constant to use for an unknown content part mime type */
public const val UNKNOWN_MIME_TYPE: String = "unknown/unknown"

/** Constant to use for an unknown content part uri */
public const val UNKNOWN_URI: String = "unknown"

/** Constant to use for an unknown content part file name */
public const val UNKNOWN_FILE_NAME: String = "unknown"

/**  Constant to use for an unknown tool call id */
public const val UNKNOWN_TOOL_CALL_ID: String = "unknown"

/** Constant to use for an unknown tool description */
public const val UNKNOWN_TOOL_DESCRIPTION: String = "unknown"

/**
 * Converts a list of [ContentBlock] of ACP prompt to a Koog [Message.User].
 */
public fun List<ContentBlock>.toKoogMessage(clock: KoogClock): Message {
    return Message.User(
        parts = this.map { it.toKoogContentPart() },
        metaInfo = RequestMetaInfo(clock.now())
    )
}

/**
 * Converts a ContentPart to an ACP ContentBlock.
 *
 * As the Koog and ACP models are slightly different, some assumptions in converters are made:
 * 1. Treat fileName as uri and vice versa, should be fixed in the future by adding uri to the file
 * 2. Stub all nullable content types with 'unknown' constants
 * 3. Assume that a format is the last segment of the MIME type
 */
public fun MessagePart.Attachment.toAcpContentBlock(): ContentBlock {
    return when (val source = this.source) {
        is AttachmentSource.Audio -> {
            when (val content = source.content) {
                is Base64,
                is AttachmentContent.Binary.Bytes -> {
                    ContentBlock.Audio(
                        data = content.asBase64(),
                        mimeType = source.mimeType,
                    )
                }

                is URL -> {
                    ContentBlock.ResourceLink(
                        name = source.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url,
                        mimeType = source.mimeType,
                    )
                }

                is PlainText -> {
                    throw IllegalArgumentException("Audio attachment can’t have plain text content")
                }
            }
        }

        is File ->
            when (val content = source.content) {
                is Base64 -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.BlobResourceContents(
                        blob = content.base64,
                        uri = source.fileName ?: UNKNOWN_URI,
                        mimeType = source.mimeType
                    )
                )

                is AttachmentContent.Binary.Bytes -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.BlobResourceContents(
                        blob = content.asBase64(),
                        uri = source.fileName ?: UNKNOWN_URI,
                        mimeType = source.mimeType
                    )
                )

                is PlainText -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.TextResourceContents(
                        text = content.text,
                        uri = source.fileName ?: UNKNOWN_URI,
                        mimeType = source.mimeType,
                    )
                )

                is URL -> {
                    ContentBlock.ResourceLink(
                        name = source.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url,
                        mimeType = source.mimeType,
                    )
                }
            }

        is AttachmentSource.Image -> {
            when (val content = source.content) {
                is Base64,
                is AttachmentContent.Binary.Bytes -> {
                    ContentBlock.Image(
                        data = content.asBase64(),
                        mimeType = source.mimeType,
                        uri = source.fileName,
                    )
                }

                is URL -> {
                    ContentBlock.ResourceLink(
                        name = source.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url,
                        mimeType = source.mimeType,
                    )
                }

                is PlainText -> {
                    throw IllegalArgumentException("Image attachment can’t have plain text content")
                }
            }
        }

        is AttachmentSource.Video -> {
            throw IllegalArgumentException("Video content is not supported yet in Acp content blocks.")
        }
    }
}

/**
 * Converts a single [ContentBlock] of ACP prompt to a Koog [ContentPart].
 *
 * As the Koog and ACP models are slightly different, some assumptions in converters are made:
 * 1. Treat fileName as uri and vice versa, should be fixed in the future by adding uri to the file
 * 2. Stub all nullable content types with 'unknown' constants
 * 3. Assume that a format is the last segment of the MIME type
 */
public fun ContentBlock.toKoogContentPart(): MessagePart.RequestPart {
    return when (this) {
        // https://agentclientprotocol.com/protocol/content#audio-content
        is ContentBlock.Audio -> {
            MessagePart.Attachment(
                AttachmentSource.Audio(
                    content = Base64(data),
                    format = parseFormat(mimeType),
                    mimeType = mimeType,
                )
            )
        }

        // https://agentclientprotocol.com/protocol/content#image-content
        is ContentBlock.Image -> {
            MessagePart.Attachment(
                AttachmentSource.Image(
                    content = Base64(data),
                    format = parseFormat(mimeType),
                    mimeType = mimeType,
                    fileName = uri
                )
            )
        }

        // https://agentclientprotocol.com/protocol/content#embedded-resource
        is ContentBlock.Resource -> {
            when (val resource = this.resource) {
                is EmbeddedResourceResource.BlobResourceContents -> {
                    MessagePart.Attachment(
                        File(
                            content = Base64(resource.blob),
                            format = parseFormat(resource.mimeType),
                            mimeType = resource.mimeType ?: UNKNOWN_MIME_TYPE,
                            fileName = resource.uri
                        )
                    )
                }

                is EmbeddedResourceResource.TextResourceContents -> {
                    MessagePart.Attachment(
                        File(
                            content = PlainText(resource.text),
                            format = parseFormat(resource.mimeType),
                            mimeType = resource.mimeType ?: UNKNOWN_MIME_TYPE,
                            fileName = resource.uri
                        )
                    )
                }
            }
        }

        // https://agentclientprotocol.com/protocol/content#resource-link
        is ContentBlock.ResourceLink -> {
            MessagePart.Attachment(
                File(
                    content = URL(uri),
                    format = parseFormat(mimeType),
                    mimeType = mimeType ?: UNKNOWN_MIME_TYPE,
                    fileName = uri
                )
            )
        }

        is ContentBlock.Text -> MessagePart.Text(text)
    }
}

/**
 * Converts a [Message.Assistant] to a list of ACP [SessionUpdateEvent].
 *
 * As the Koog and ACP models are slightly different, some assumptions in converters are made:
 * 1. Stub all nullable content types with 'unknown' constants
 */
public fun Message.Assistant.toAcpEvents(tools: List<ToolDescriptor> = emptyList()): List<SessionUpdateEvent> {
    val response = this
    return buildList {
        response.parts.forEach { part ->
            when (part) {
                is MessagePart.Reasoning -> {
                    part.content.forEach {
                        add(
                            SessionUpdateEvent(
                                update = SessionUpdate.AgentThoughtChunk(
                                    content = ContentBlock.Text(it)
                                )
                            )
                        )
                    }
                }

                is MessagePart.Tool.Call -> {
                    add(
                        SessionUpdateEvent(
                            update = SessionUpdate.ToolCall(
                                toolCallId = ToolCallId(part.id ?: UNKNOWN_TOOL_CALL_ID),
                                title = tools.firstOrNull { it.name == part.tool }?.description
                                    ?: UNKNOWN_TOOL_DESCRIPTION,
                                // TODO: Support kind for tools
                                status = ToolCallStatus.PENDING,
                                rawInput = part.argsJson,
                            )
                        )
                    )
                }

                is MessagePart.Text -> {
                    add(
                        SessionUpdateEvent(
                            update = AgentMessageChunk(ContentBlock.Text(part.text))
                        )
                    )
                }

                is MessagePart.Attachment -> {
                    add(
                        SessionUpdateEvent(
                            update = AgentMessageChunk(part.toAcpContentBlock())
                        )
                    )
                }
            }
        }
    }
}

/**
 * Attempts to derive a content part format from a MIME type.
 *
 * ACP entities expose only the MIME type and not the format separately,
 * which prevents retrieving the format directly for Koog entities.
 * To work around this, the method assumes that the format corresponds to the last segment of the MIME type
 * (which is not always guaranteed to be correct).
 */
private fun parseFormat(mimeType: String?): String {
    return mimeType?.split("/")?.lastOrNull() ?: UNKNOWN_FORMAT
}
