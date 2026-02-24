package ai.koog.prompt.message

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a part of a message.
 * Parts can be text [ContentPart.Text], images [ContentPart.Image], audio [ContentPart.Audio], or files [ContentPart.File].
 * All attachment parts (all except [ContentPart.Text]) must implement [Attachment] interface.
 */
@Serializable
public sealed interface ContentPart {
    /**
     * Represents different types of content that can be attached to messages.
     * Check nested implementation classes for supported formats and details.
     */
    @Serializable
    public sealed interface Attachment : ContentPart {
        /**
         * Attachment content.
         */
        public val content: AttachmentContent

        /**
         * File format (usually file extension) of the attachment file.
         * E.g. jpg, png, mp4, pdf.
         */
        public val format: String

        /**
         * MIME type of the attachment
         * E.g. image/jpg, video/mp4
         */
        public val mimeType: String

        /**
         * Optional file name of the attachment file.
         */
        public val fileName: String?
    }

    /**
     * Text content part in the OpenAI chat completion API.
     *
     *
     * @property text The text content.
     */
    @Serializable
    @SerialName("text")
    public data class Text(public val text: String) : ContentPart

    /**
     * Image attachment (jpg, png, gif, etc.).
     */
    @Serializable
    public data class Image(
        override val content: AttachmentContent,
        override val format: String,
        override val mimeType: String = "image/$format",
        override val fileName: String? = null,
    ) : Attachment {
        init {
            require(content !is AttachmentContent.PlainText) { "Image can't have plain text content" }
        }
    }

    /**
     * Video attachment (mp4, avi, etc.).
     */
    @Serializable
    public data class Video(
        override val content: AttachmentContent,
        override val format: String,
        override val mimeType: String = "video/$format",
        override val fileName: String? = null,
    ) : Attachment {
        init {
            require(content !is AttachmentContent.PlainText) { "Video can't have plain text content" }
        }
    }

    /**
     * Audio attachment (mp3, wav, etc.).
     */
    @Serializable
    public data class Audio(
        override val content: AttachmentContent,
        override val format: String,
        override val mimeType: String = "audio/$format",
        override val fileName: String? = null,
    ) : Attachment {
        init {
            require(content !is AttachmentContent.PlainText) { "Audio can't have plain text content" }
        }
    }

    /**
     * Other types of file attachments.
     * E.g. pdf, md, txt.
     */
    @Serializable
    public data class File(
        override val content: AttachmentContent,
        override val format: String,
        override val mimeType: String,
        override val fileName: String? = null,
    ) : Attachment
}
