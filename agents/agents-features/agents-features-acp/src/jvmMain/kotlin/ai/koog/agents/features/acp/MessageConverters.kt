package ai.koog.agents.features.acp

import ai.koog.agents.core.tools.ToolDescriptor
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
public fun List<ContentBlock>.toKoogMessage(clock: Clock): Message {
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
public fun ContentPart.toAcpContentBlock(): ContentBlock {
    return when (this) {
        is ContentPart.Text -> {
            ContentBlock.Text(this.text)
        }

        is ContentPart.Audio -> {
            when (val content = this.content) {
                is AttachmentContent.Binary.Base64,
                is AttachmentContent.Binary.Bytes -> {
                    ContentBlock.Audio(
                        data = content.asBase64(),
                        mimeType = this.mimeType,
                    )
                }

                is AttachmentContent.URL -> {
                    ContentBlock.ResourceLink(
                        name = this.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url,
                        mimeType = this.mimeType,
                    )
                }

                is AttachmentContent.PlainText -> {
                    throw IllegalArgumentException("Audio attachment can’t have plain text content")
                }
            }
        }

        is ContentPart.File ->
            when (val content = this.content) {
                is AttachmentContent.Binary.Base64 -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.BlobResourceContents(
                        blob = content.base64,
                        uri = this.fileName ?: UNKNOWN_URI,
                        mimeType = this.mimeType
                    )
                )

                is AttachmentContent.Binary.Bytes -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.BlobResourceContents(
                        blob = content.asBase64(),
                        uri = this.fileName ?: UNKNOWN_URI,
                        mimeType = this.mimeType
                    )
                )

                is AttachmentContent.PlainText -> ContentBlock.Resource(
                    resource = EmbeddedResourceResource.TextResourceContents(
                        text = content.text,
                        uri = this.fileName ?: UNKNOWN_URI,
                        mimeType = this.mimeType,
                    )
                )

                is AttachmentContent.URL -> {
                    ContentBlock.ResourceLink(
                        name = this.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url,
                        mimeType = this.mimeType,
                    )
                }
            }

        is ContentPart.Image -> {
            when (val content = this.content) {
                is AttachmentContent.Binary.Base64,
                is AttachmentContent.Binary.Bytes -> {
                    ContentBlock.Image(
                        data = content.asBase64(),
                        mimeType = this.mimeType,
                        uri = this.fileName,
                    )
                }

                is AttachmentContent.URL -> {
                    ContentBlock.ResourceLink(
                        name = this.fileName ?: UNKNOWN_FILE_NAME,
                        uri = content.url,
                        mimeType = this.mimeType,
                    )
                }

                is AttachmentContent.PlainText -> {
                    throw IllegalArgumentException("Image attachment can’t have plain text content")
                }
            }
        }

        is ContentPart.Video -> {
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
public fun ContentBlock.toKoogContentPart(): ContentPart {
    return when (this) {
        // https://agentclientprotocol.com/protocol/content#audio-content
        is ContentBlock.Audio -> {
            ContentPart.Audio(
                content = AttachmentContent.Binary.Base64(data),
                format = parseFormat(mimeType),
                mimeType = mimeType,
            )
        }

        // https://agentclientprotocol.com/protocol/content#image-content
        is ContentBlock.Image -> {
            ContentPart.Image(
                content = AttachmentContent.Binary.Base64(data),
                format = parseFormat(mimeType),
                mimeType = mimeType,
                fileName = uri
            )
        }

        // https://agentclientprotocol.com/protocol/content#embedded-resource
        is ContentBlock.Resource -> {
            when (val resource = this.resource) {
                is EmbeddedResourceResource.BlobResourceContents -> {
                    ContentPart.File(
                        content = AttachmentContent.Binary.Base64(resource.blob),
                        format = parseFormat(resource.mimeType),
                        mimeType = resource.mimeType ?: UNKNOWN_MIME_TYPE,
                        fileName = resource.uri
                    )
                }

                is EmbeddedResourceResource.TextResourceContents -> {
                    ContentPart.File(
                        content = AttachmentContent.PlainText(resource.text),
                        format = parseFormat(resource.mimeType),
                        mimeType = resource.mimeType ?: UNKNOWN_MIME_TYPE,
                        fileName = resource.uri
                    )
                }
            }
        }

        // https://agentclientprotocol.com/protocol/content#resource-link
        is ContentBlock.ResourceLink -> {
            ContentPart.File(
                content = AttachmentContent.URL(uri),
                format = parseFormat(mimeType),
                mimeType = mimeType ?: UNKNOWN_MIME_TYPE,
                fileName = uri
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
 *
 * As the Koog and ACP models are slightly different, some assumptions in converters are made:
 * 1. Stub all nullable content types with 'unknown' constants
 */
public fun Message.Response.toAcpEvents(tools: List<ToolDescriptor> = emptyList()): List<SessionUpdateEvent> {
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
                            title = tools.firstOrNull { it.name == response.tool }?.description
                                ?: UNKNOWN_TOOL_DESCRIPTION,
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
