package ai.koog.agents.a2a.core

import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FilePart
import ai.koog.a2a.model.FileWithBytes
import ai.koog.a2a.model.FileWithUri
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Alias to A2A message type, to avoid clashing with Koog's message type.
 * @see [ai.koog.a2a.model.Message]
 */
public typealias A2AMessage = ai.koog.a2a.model.Message

private val json = Json {
    prettyPrint = true
}

/**
 * Converts [A2AMessage] to Koog's [Message].
 *
 * @param clock The clock to use for the timestamp. Defaults to [Clock.System].
 */
public fun A2AMessage.toKoogMessage(
    clock: Clock = Clock.System,
): Message {
    val content = StringBuilder()
    val attachments = mutableListOf<Attachment>()

    // Put ids information in the text content, since Koog doesn't have special fields for them.
    contextId?.let {
        content.appendLine("Context ID: $it")
    }

    taskId?.let {
        content.appendLine("Task ID: $it")
    }

    referenceTaskIds?.forEach {
        content.appendLine("Reference Task ID: $it")
    }

    parts.forEach { part ->
        when (part) {
            is TextPart -> content.appendLine(part.text)
            // Koog doesn't support structured data as a separate type, just append it to the content.
            is DataPart -> content.appendLine(json.encodeToString(part.data))
            is FilePart -> {
                val file = part.file

                val attachment = Attachment.File(
                    // do not have that information separately in A2A
                    format = "",
                    // if no mime type is provided, assume it's arbitrary binary data
                    mimeType = file.mimeType ?: "application/octet-stream",
                    fileName = file.name,
                    content = when (file) {
                        is FileWithBytes -> AttachmentContent.Binary.Base64(file.bytes)
                        is FileWithUri -> AttachmentContent.URL(file.uri)
                    }
                )

                attachments.add(attachment)
            }
        }
    }

    return when (role) {
        Role.User -> Message.User(
            content = content.toString(),
            metaInfo = RequestMetaInfo(
                timestamp = clock.now()
            ),
            attachments = attachments.toList(),
        )

        Role.Agent -> Message.Assistant(
            content = content.toString(),
            metaInfo = ResponseMetaInfo(
                timestamp = clock.now()
            ),
            attachments = attachments,
        )
    }
}

/**
 * Converts Koog's [Message] to [A2AMessage].
 *
 * @see ai.koog.a2a.model.Message
 */
@OptIn(ExperimentalUuidApi::class)
public fun Message.toA2AMessage(
    messageId: String = Uuid.random().toString(),
    contextId: String? = null,
    taskId: String? = null,
    referenceTaskIds: List<String>? = null,
    metadata: JsonObject? = null,
    extensions: List<String>? = null,
): A2AMessage {
    val role = when (this) {
        is Message.User -> Role.User
        is Message.Assistant -> Role.Agent
        else -> throw IllegalArgumentException("A2A can't handle this Koog message type: $this")
    }

    val parts = mutableListOf<Part>()

    // Add content
    parts.add(TextPart(content))

    // Add attachments
    attachments.forEach { attachment ->
        val file = when (val content = attachment.content) {
            // Plain text files are not supported, convert them to binary files.
            is AttachmentContent.PlainText -> FileWithBytes(
                bytes = AttachmentContent.Binary.Bytes(content.text.encodeToByteArray())
                    .asBase64(),
                name = attachment.fileName,
                mimeType = attachment.mimeType,
            )

            is AttachmentContent.Binary -> FileWithBytes(
                bytes = content.asBase64(),
                name = attachment.fileName,
                mimeType = attachment.mimeType,
            )

            is AttachmentContent.URL -> FileWithUri(
                uri = content.url,
                name = attachment.fileName,
                mimeType = attachment.mimeType,
            )
        }

        parts.add(FilePart(file))
    }

    return A2AMessage(
        messageId = messageId,
        role = role,
        parts = parts,
        extensions = extensions,
        taskId = taskId,
        referenceTaskIds = referenceTaskIds,
        contextId = contextId,
        metadata = metadata
    )
}
