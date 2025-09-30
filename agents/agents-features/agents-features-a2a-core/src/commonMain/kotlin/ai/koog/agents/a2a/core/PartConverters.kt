package ai.koog.agents.a2a.core

import ai.koog.a2a.model.DataPart
import ai.koog.a2a.model.FilePart
import ai.koog.a2a.model.FileWithBytes
import ai.koog.a2a.model.FileWithUri
import ai.koog.a2a.model.Part
import ai.koog.a2a.model.TextPart
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Koog doesn't have proper support for message parts yet, but A2A operates with parts.
 * This is a helper mapping structure, to map A2A parts either to part of the textual content or to the attachment.
 */
@Serializable
public sealed interface KoogPart

/**
 * Text content part representing part of the [ai.koog.prompt.message.Message.content]
 */
@Serializable
public data class KoogContentPart(val content: String) : KoogPart

/**
 * Attachment part representing part of the [ai.koog.prompt.message.Message.Assistant.attachments] or
 * [ai.koog.prompt.message.Message.User.attachments]
 */
@Serializable
public data class KoogAttachmentPart(val attachment: Attachment) : KoogPart

private val json = Json {
    prettyPrint = true
}

/**
 * Converts A2A [Part] to Koog [KoogPart].
 */
public fun Part.toKoogPart(): KoogPart = when (this) {
    is TextPart -> KoogContentPart(this.text)
    // Koog doesn't support structured data as a separate type, treat it as a content part.

    is DataPart -> KoogContentPart(json.encodeToString(this.data))

    is FilePart -> {
        val file = this.file // to enable smart cast

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

        KoogAttachmentPart(attachment)
    }
}

/**
 * Converts Koog [KoogPart] to A2A [Part].
 */
public fun KoogPart.toA2APart(): Part = when (this) {
    is KoogContentPart -> TextPart(this.content)

    is KoogAttachmentPart -> {
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

        FilePart(file)
    }
}

/**
 * Helper method to convert an iterable of [KoogPart] to the pair of the text content and attachments.
 */
public fun Iterable<KoogPart>.toContentWithAttachments(): Pair<String, List<Attachment>> {
    val content = StringBuilder()
    val attachments = mutableListOf<Attachment>()

    forEach { part ->
        when (part) {
            is KoogContentPart -> content.appendLine(part.content)
            is KoogAttachmentPart -> attachments.add(part.attachment)
        }
    }

    return content.toString().trim() to attachments
}
