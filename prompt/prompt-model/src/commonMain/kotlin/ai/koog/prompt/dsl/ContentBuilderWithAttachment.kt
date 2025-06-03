package ai.koog.prompt.dsl

import ai.koog.prompt.message.MediaContent
import ai.koog.prompt.text.TextContentBuilder

/**
 * A builder class that extends TextContentBuilder to support both text and media attachments.
 *
 * This class combines text content building capabilities with media attachment support,
 * allowing users to create rich content that includes both textual information and
 * media elements like images, audio files, and documents.
 *
 * Example usage:
 * ```kotlin
 * val contentBuilder = ContentBuilderWithAttachment()
 * contentBuilder.text("Here's my analysis:")
 * contentBuilder.attachments {
 *     image("chart.png")
 *     document("report.pdf")
 * }
 * val (text, attachments) = contentBuilder.buildWithAttachments()
 * ```
 *
 * This class is part of the new DSL structure for prompt construction, replacing the previous
 * UserContentBuilder approach. It provides a more structured way to combine text and media
 * attachments in a single builder.
 *
 * @see TextContentBuilder for text-only content building
 * @see AttachmentBuilder for media attachment building
 */
@PromptDSL
public class ContentBuilderWithAttachment : TextContentBuilder() {
    private var attachments: List<MediaContent> = emptyList()

    /**
     * Configures media attachments for this content builder.
     *
     * This method allows you to specify multiple media attachments using the
     * AttachmentBuilder DSL. The attachments can include images, audio files,
     * and documents.
     *
     * Example:
     * ```kotlin
     * attachments {
     *     image("photo.jpg")
     *     audio(audioData, "mp3")
     *     document("report.pdf")
     * }
     * ```
     *
     * @param body The configuration block for building attachments using AttachmentBuilder
     */
    public fun attachments(body: AttachmentBuilder.() -> Unit) {
        this.attachments = AttachmentBuilder().apply(body).build()
    }

    /**
     * Builds and returns both the text content and media attachments.
     *
     * This method combines the text content built through the inherited TextContentBuilder
     * methods with any media attachments configured through the [attachments] method.
     *
     * @return A Pair containing the built text content as the first element and
     *         the list of media attachments as the second element
     */
    public fun buildWithAttachments(): Pair<String, List<MediaContent>> = build() to attachments
}
