package ai.koog.prompt.message

import ai.koog.utils.serializers.ByteArrayAsBase64Serializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Represents different types of content that can be attached to messages.
 * Check nested implementation classes for supported formats and details.
 */
@Serializable
public sealed interface Attachment {
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

/**
 * Content of the attachment, check implementation nested classes for supported content types.
 */
@Serializable
public sealed interface AttachmentContent {
    /**
     * Plain text content.
     */
    @Serializable
    public data class PlainText(val text: String) : AttachmentContent {
        override fun toString(): String {
            return "PlainText(length=${text.length})"
        }
    }

    /**
     * URL of the content (e.g. image or a document).
     */
    @Serializable
    public data class URL(val url: String) : AttachmentContent

    /**
     * Binary content.
     */
    @Serializable
    public sealed interface Binary : AttachmentContent {
        /**
         * Base64 representation of the binary content
         */
        public fun asBase64(): String

        /**
         * Returns the binary content as a byte array.
         *
         * @return a ByteArray representing the binary content.
         */
        public fun asBytes(): ByteArray

        /**
         * Binary content represented as byte array.
         */
        @Serializable
        public data class Bytes(
            @SerialName("base64")
            @Serializable(with = ByteArrayAsBase64Serializer::class)
            val data: ByteArray
        ) : Binary {

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Bytes) return false

                if (!data.contentEquals(other.data)) return false

                return true
            }

            override fun hashCode(): Int {
                return data.contentHashCode()
            }

            override fun toString(): String {
                return "Bytes(length=${data.size})"
            }

            @OptIn(ExperimentalEncodingApi::class)
            override fun asBase64(): String = kotlin.io.encoding.Base64.encode(data)

            override fun asBytes(): ByteArray = data
        }

        /**
         * Binary content represented as Base64 encoded string.
         */
        @Serializable
        public data class Base64(val base64: String) : Binary {
            override fun toString(): String {
                return "Base64(length=${base64.length})"
            }

            /**
             * Decodes the Base64 encoded string to its corresponding byte array.
             *
             * @return a ByteArray representation of the decoded Base64 string.
             */
            @OptIn(ExperimentalEncodingApi::class)
            public override fun asBytes(): ByteArray = kotlin.io.encoding.Base64.decode(base64)

            override fun asBase64(): String = base64
        }
    }
}
