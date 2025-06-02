package ai.koog.prompt.dsl

import ai.koog.prompt.message.MediaContent
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.text.TextContentBuilder
import kotlinx.datetime.Clock

/**
 * A builder for constructing user messages with support for text and media content.
 *
 * Provides a fluent API for creating user messages that can include text, images, audio,
 * and document attachments. The builder pattern enables convenient construction of complex
 * message content with multiple components.
 */
public class UserContentBuilder(private val clock: Clock = Clock.System) {
    /**
     * Internal collection to accumulate user messages during the building process.
     *
     * This mutable list stores all the messages created through the various builder methods
     * and is used to construct the final message list when [build] is called.
     */
    private val messages = mutableListOf<Message>()

    /**
     * Adds a simple text message to the user content.
     *
     * @param text The text content to be included in the user message.
     */
    public fun text(text: String) {
        messages.add(Message.User(text, metaInfo = RequestMetaInfo.create(clock)))
    }

    /**
     * Adds a text message using a [TextContentBuilder] for advanced text formatting.
     *
     * This method allows for complex text construction using the builder pattern,
     * supporting features like newlines, padding, and structured text content.
     *
     * @param body A lambda function applied to a [TextContentBuilder] instance for constructing formatted text.
     */
    public fun text(body: TextContentBuilder.() -> Unit) {
        text(TextContentBuilder().apply(body).build())
    }

    /**
     * Adds an image message to the user content.
     *
     * Creates a user message containing an image from the specified source.
     * The source can be either a local file path or a URL.
     *
     * @param source The path to the local image file or URL of the image.
     */
    public fun image(source: String) {
        messages.add(
            Message.User(
                "",
                metaInfo = RequestMetaInfo.create(clock),
                mediaContent = MediaContent.Image(source)
            )
        )
    }

    /**
     * Adds an audio message to the user content.
     *
     * Creates a user message containing audio data with the specified format.
     * The audio data should be provided as a byte array.
     *
     * @param data The audio data as a byte array.
     * @param format The audio file format (e.g., "mp3", "wav").
     */
    public fun audio(data: ByteArray, format: String) {
        messages.add(
            Message.User(
                "",
                metaInfo = RequestMetaInfo.create(clock),
                mediaContent = MediaContent.Audio(data = data, format = format)
            )
        )
    }

    /**
     * Adds a document message to the user content.
     *
     * Creates a user message containing a document file from the specified local path.
     * URLs are not supported for security reasons.
     *
     * @param source The local file path to the document.
     */
    public fun document(source: String) {
        messages.add(
            Message.User(
                "",
                metaInfo = RequestMetaInfo.create(clock),
                mediaContent = MediaContent.File(source)
            )
        )
    }

    /**
     * Constructs and returns the accumulated list of user messages.
     *
     * @return A list containing all the user messages created through the builder methods.
     */
    public fun build(): List<Message> = messages
}