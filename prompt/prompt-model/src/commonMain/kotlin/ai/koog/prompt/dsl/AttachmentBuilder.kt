package ai.koog.prompt.dsl

import ai.koog.prompt.message.MediaContent

/**
 * A builder for constructing media attachments for prompt messages.
 *
 * This builder provides a fluent DSL API for creating collections of media content
 * that can be attached to user messages. It supports images, audio files, and documents,
 * enabling rich multimedia content in prompt construction.
 *
 * Example usage:
 * ```kotlin
 * val attachments = AttachmentBuilder().apply {
 *     image("screenshot.png")
 *     audio(audioData, "mp3")
 *     document("report.pdf")
 * }.build()
 * ```
 *
 * This class is part of the new DSL structure for prompt construction. It focuses specifically
 * on building media attachments, while text content is handled by TextContentBuilder.
 * For combining both text and attachments, use ContentBuilderWithAttachment.
 *
 * @see MediaContent for the types of media content supported
 * @see ContentBuilderWithAttachment for using this builder in prompt construction
 */
@PromptDSL
public class AttachmentBuilder() {
    /**
     * Internal collection to accumulate media content during the building process.
     *
     * This mutable list stores all the media content items created through the various builder methods
     * and is used to construct the final media content list when [build] is called.
     */
    private val mediaContents = mutableListOf<MediaContent>()

    /**
     * Adds an image attachment to the media content collection.
     *
     * Creates an image media content item from the specified source.
     * The source can be either a local file path or a URL.
     *
     * Model type support:
     * - **Anthropic** — supports local and URL images. Formats: `png`, `jpeg`, `webp`, `gif`
     * - **Gemini** — supports local images only. Formats: `png`, `jpeg`, `webp`, `heic`, `heif`, `gif`
     * - **Ollama** — does not support images yet
     * - **OpenAI** — supports local and URL images. Formats: `png`, `jpeg`, `webp`, `gif`
     * - **OpenRouter** — supports local and URL images. Formats: `png`, `jpeg`, `webp`, `gif`
     *
     * Example:
     * ```kotlin
     * image("screenshot.png")           // Local file
     * image("https://example.com/pic.jpg") // URL
     * ```
     *
     * @param source The path to the local image file or URL of the image
     */
    public fun image(source: String) {
        mediaContents.add(MediaContent.Image(source))
    }

    /**
     * Adds an audio attachment to the media content collection.
     *
     * Creates an audio media content item with the specified data and format.
     * The audio data should be provided as a byte array.
     *
     * - **Anthropic** — does not support audio
     * - **Gemini** — formats: `wav`, `mp3`, `aiff`, `aac`, `ogg`, `flac`
     * - **Ollama** — does not support audio
     * - **OpenAI** — formats: `wav`, `mp3`
     * - **OpenRouter** — formats: `wav`, `mp3`
     *
     * Example:
     * ```kotlin
     * audio(audioByteArray, "mp3")
     * audio(recordedSpeech, "wav")
     * ```
     *
     * @param data The audio data as a byte array
     * @param format The audio file format (e.g., "mp3", "wav", "ogg")
     */
    public fun audio(data: ByteArray, format: String) {
        mediaContents.add(MediaContent.Audio(data, format))
    }

    /**
     * Adds a document attachment to the media content collection.
     *
     * Creates a document media content item from the specified local file path.
     * URLs are not supported for security reasons.
     *
     * - Anthropic — supports local and URL-based PDF files.  Local support also for TXT and MD files.
     * - Gemini — supports only local files of the following formats: `pdf`, `js`, `py`, `txt`, `html`, `css`, `md`, `csv`, `xml`, `rtf`
     * - Ollama — does not support documents yet
     * - OpenAI — supports only local PDF files
     * - OpenRouter — supports only local PDF files
     *
     * Example:
     * ```kotlin
     * document("report.pdf")
     * document("/path/to/document.docx")
     * ```
     *
     * @param source The local file path to the document
     */
    public fun document(source: String) {
        mediaContents.add(MediaContent.File(source))
    }

    /**
     * Constructs and returns the accumulated list of media content items.
     *
     * This method finalizes the building process and returns all the media content
     * items that were added through the various builder methods.
     *
     * @return A list containing all the media content items created through the builder methods
     */
    public fun build(): List<MediaContent> = mediaContents
}
