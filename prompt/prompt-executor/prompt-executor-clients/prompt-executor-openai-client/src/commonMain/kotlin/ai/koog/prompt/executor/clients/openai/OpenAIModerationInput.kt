package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.executor.clients.openai.OpenAIModerationInput.Companion.text
import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a moderation input for OpenAI's moderation API.
 * This can be either text or an image.
 */
@ConsistentCopyVisibility
@Serializable
internal data class OpenAIModerationInput private constructor(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
) {
    /**
     * Companion object for the OpenAIModerationInput class, providing factory methods to create instances
     * for text or image moderation.
     */
    public companion object {
        /**
         * Creates a new OpenAIModerationInput instance configured to represent textual input.
         *
         * @param text The textual content to be moderated.
         * @return An OpenAIModerationInput instance configured with the specified text and a type of "text".
         */
        public fun text(text: String): OpenAIModerationInput = OpenAIModerationInput(type = "text", text = text)

        /**
         * Converts a given image URL into an `OpenAIModerationInput` instance with the appropriate type and URL.
         *
         * @param imageUrl The URL of the image to be used as input for moderation.
         * @return An instance of `OpenAIModerationInput` containing the specified image URL and type.
         */
        public fun imageFromUrl(imageUrl: String): OpenAIModerationInput =
            OpenAIModerationInput(type = "image_url", imageUrl = imageUrl)

        /**
         * Converts a base64-encoded image into an `OpenAIModerationInput` object.
         *
         * @param data The base64-encoded string representation of the image.
         * @param mimeType The MIME type of the image (e.g., "image/png", "image/jpeg").
         * @return An `OpenAIModerationInput` object containing the image URL in a format suitable for OpenAI's moderation API input.
         */
        public fun imageFromBase64(data: String, mimeType: String): OpenAIModerationInput =
            OpenAIModerationInput(type = "image_url", imageUrl = "data:$mimeType;base64,$data\"")

        /**
         * Converts an `Attachment` to an `OpenAIModerationInput` object, supporting only image attachments.
         *
         * Throws an `IllegalArgumentException` if the provided attachment is not an image or contains unsupported content.
         *
         * @param media The `Attachment` instance to be converted, which must be of type `Attachment.Image`.
         * @return An instance of `OpenAIModerationInput` created from the image content, either based on a URL or base64 representation.
         */
        public fun fromImageContent(media: Attachment): OpenAIModerationInput {
            if (media !is Attachment.Image) {
                throw IllegalArgumentException("Only image content is supported for moderation: ${media::class.simpleName}")
            }

            return when (media.content) {
                is AttachmentContent.URL -> imageFromUrl((media.content as AttachmentContent.URL).url)
                is AttachmentContent.Binary.Base64 -> imageFromBase64(
                    (media.content as AttachmentContent.Binary.Base64).base64,
                    media.mimeType
                )

                else -> throw IllegalArgumentException("Unsupported image attachment content: ${media.content::class}")
            }
        }
    }
}
