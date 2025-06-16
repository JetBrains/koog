package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.openai.ImageSource.Companion.text
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a moderation input for OpenAI's moderation API.
 * This can be either text or an image.
 */
@Serializable
public sealed class OpenAIModerationInput {
    /**
     * Represents a text input for moderation.
     *
     * @property text The text to moderate.
     */
    @Serializable
    public data class Text(
        val text: String,
        val type: String = "text"
    ) : OpenAIModerationInput()

    /**
     * Represents an image input for moderation.
     *
     * @property imageUrl The URL or base64 data of the image to moderate.
     */
    @Serializable
    public data class Image(
        @SerialName("image_url") val imageUrl: ImageSource,
        val type: String = "image_url"
    ) : OpenAIModerationInput()
}

/**
 * Represents an image source for moderation.
 * This can be either a URL or base64-encoded image data.
 */
@Serializable
public sealed interface ImageSource {
    /**
     * The URL or base64 data of the image.
     */
    public val url: String

    /**
     * Represents an image URL.
     *
     * @property url The URL of the image.
     */
    @Serializable
    public data class Url(
        override val url: String
    ) : ImageSource

    /**
     * Represents a base64-encoded image.
     *
     * @property data The base64-encoded image data.
     * @property mediaType The media type of the image (e.g., "image/png").
     */
    @Serializable
    public data class Base64(
        val data: String,
        val mediaType: String
    ) : ImageSource {
        override val url: String
            get() = "data:$mediaType;base64,$data"
    }

    /**
     * Companion object with utility methods for creating moderation inputs.
     */
    public companion object {
        /**
         * Creates a text moderation input.
         *
         * @param text The text to moderate
         * @return A text moderation input
         */
        public fun text(text: String): OpenAIModerationInput.Text {
            return OpenAIModerationInput.Text(text)
        }

        /**
         * Creates an image moderation input from a URL.
         *
         * @param url The URL of the image to moderate
         * @return An image moderation input
         */
        public fun imageUrl(url: String): OpenAIModerationInput.Image {
            return OpenAIModerationInput.Image(ImageSource.Url(url))
        }

        /**
         * Creates an image moderation input from base64-encoded data.
         *
         * @param data The base64-encoded image data
         * @param mediaType The media type of the image (e.g., "image/png")
         * @return An image moderation input
         */
        public fun imageBase64(data: String, mediaType: String): OpenAIModerationInput.Image {
            return OpenAIModerationInput.Image(ImageSource.Base64(data, mediaType))
        }

        /**
         * Creates a moderation input from a MediaContent.Image object.
         *
         * @param media The image media content to convert
         * @return A moderation input for the image
         * @throws IllegalArgumentException if the media is not an image
         */
        public fun fromImageContent(media: Attachment): OpenAIModerationInput {
            if (media !is Attachment.Image) {
                throw IllegalArgumentException("Only image content is supported for moderation: ${media::class.simpleName}")
            }

            return when (media.content) {
                is AttachmentContent.URL -> imageUrl((media.content as AttachmentContent.URL).url)
                is AttachmentContent.Binary.Base64 -> imageBase64(
                    (media.content as AttachmentContent.Binary.Base64).base64,
                    media.mimeType
                )
                else -> throw IllegalArgumentException("Unsupported image attachment content: ${media.content::class}")
            }
        }
    }
}
