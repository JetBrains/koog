package ai.koog.prompt.dsl

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.text.TextContentBuilderBase
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString

/**
 * A builder for constructing parts for prompt messages.
 * All parts are added to a list in declaration order and can be retrieved through the [build] method.
 *
 * Example usage:
 * ```kotlin
 * val parts = ContentPartsBuilder().apply {
 *     text("Hello!")
 *     image("screenshot.png")
 *     binaryFile("report.pdf")
 * }.build()
 * ```
 *
 * @see ContentPart
 */
@PromptDSL
public class ContentPartsBuilder : TextContentBuilderBase<List<ContentPart>>() {
    private val parts = mutableListOf<ContentPart>()

    private class FileData(val name: String, val extension: String)

    private fun String.urlFileData(): FileData {
        val urlRegex = "^https?://.*$".toRegex()
        require(this.matches(urlRegex)) { "Invalid url: $this" }

        val name = this
            .substringBeforeLast("?")
            .substringBeforeLast("#")
            .substringAfterLast("/")

        val extension = name
            .substringAfterLast(".", "")
            .takeIf { it.isNotEmpty() }
            ?.lowercase() ?: throw IllegalArgumentException("File extension not found in url: $this")

        return FileData(name, extension)
    }

    private fun Path.fileData(): FileData {
        require(SystemFileSystem.exists(this)) { "File not found: $this" }
        require(SystemFileSystem.metadataOrNull(this)?.isRegularFile == true) { "This is not a regular file: $this" }

        val extension = this.name.substringAfterLast(".", "")
            .takeIf { it.isNotEmpty() }
            ?.lowercase() ?: throw IllegalArgumentException("File extension not found in path: $this")

        return FileData(this.name, extension)
    }

    private fun Path.readText(): String {
        return SystemFileSystem.source(this).buffered().use { it.readString() }
    }

    private fun Path.readByteArray(): ByteArray {
        return SystemFileSystem.source(this).buffered().use { it.readByteArray() }
    }

    /**
     * Flushes the text builder and adds its content as a text part if there is any.
     */
    private fun flushTextBuilder() {
        if (textBuilder.isNotEmpty()) {
            parts.add(ContentPart.Text(textBuilder.toString()))
            textBuilder.clear()
        }
    }

    /**
     * Adds [ContentPart] to the list of parts.
     */
    public fun part(contentPart: ContentPart) {
        // If there were some text accumulated, flush it to the text part
        flushTextBuilder()
        parts.add(contentPart)
    }

    /**
     * Adds [ContentPart.Text] to the list of parts.
     */
    public fun text(part: ContentPart.Text) {
        part(part)
    }

    /**
     * Adds [ContentPart.Image] to the list of parts.
     */
    public fun image(image: ContentPart.Image) {
        part(image)
    }

    /**
     * Adds [ContentPart.Image] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Image URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun image(url: String) {
        val fileData = url.urlFileData()
        image(
            ContentPart.Image(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.Image] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local image file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun image(path: Path) {
        val fileData = path.fileData()
        image(
            ContentPart.Image(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.Audio] to the list of parts.
     */
    public fun audio(audio: ContentPart.Audio) {
        parts.add(audio)
    }

    /**
     * Adds [ContentPart.Audio] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Audio URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun audio(url: String) {
        val fileData = url.urlFileData()
        audio(
            ContentPart.Audio(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.Audio] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local audio file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun audio(path: Path) {
        val fileData = path.fileData()
        audio(
            ContentPart.Audio(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.Video] to the list of parts.
     */
    public fun video(video: ContentPart.Video) {
        parts.add(video)
    }

    /**
     * Adds [ContentPart.Video] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Video URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun video(url: String) {
        val fileData = url.urlFileData()
        video(
            ContentPart.Video(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.Video] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local video file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun video(path: Path) {
        val fileData = path.fileData()
        video(
            ContentPart.Video(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.File] to the list of parts.
     */
    public fun file(file: ContentPart.File) {
        parts.add(file)
    }

    /**
     * Adds [ContentPart.File] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url File URL
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun file(url: String, mimeType: String) {
        val fileData = url.urlFileData()
        file(
            ContentPart.File(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.File] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local file
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun binaryFile(path: Path, mimeType: String) {
        val fileData = path.fileData()
        file(
            ContentPart.File(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [ContentPart.File] with [AttachmentContent.PlainText] content from the provided local file path.
     *
     * @param path Path to local file
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun textFile(path: Path, mimeType: String) {
        val fileData = path.fileData()
        file(
            ContentPart.File(
                content = AttachmentContent.PlainText(path.readText()),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            )
        )
    }

    /**
     * Configures media attachments for this content builder.
     */
    @Deprecated("Redundant, attach parts without attachments block")
    public fun attachments(body: ContentPartsBuilder.() -> Unit) {
        ContentPartsBuilder().apply(body).build().forEach { part(it) }
    }

    /**
     * Constructs and returns the accumulated list of attachment items.
     *
     * @return A list containing all the attachment items created through the builder methods
     */
    public override fun build(): List<ContentPart> {
        // If there were some text accumulated, flush it to the text part
        flushTextBuilder()
        return parts
    }
}
