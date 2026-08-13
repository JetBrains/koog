package ai.koog.prompt.dsl

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.message.CacheControl
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.text.TextContentBuilderBase
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString

/**
 * A base utility class for building and manipulating content based on the text.
 * This class can be extended to support more types of text-based content, e.g. text with attachments or some metadata.
 *
 * Provides methods for constructing formatted strings with features such as inserting text,
 * adding new lines, and applying padding. The builder pattern supports a fluent and convenient
 * approach to managing text content.
 */
@PromptDSL
public abstract class ContentPartsBuilderBase<T> : TextContentBuilderBase<T>() {
    protected val contentParts: MutableList<MessagePart.ContentPart> = mutableListOf()

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
    protected fun flushTextBuilder() {
        if (textBuilder.isNotEmpty()) {
            contentParts.add(MessagePart.Text(textBuilder.toString()))
            textBuilder.clear()
        }
    }

    /**
     * Adds [MessagePart] to the list of parts.
     */
    public fun part(part: MessagePart.ContentPart) {
        // If there were some text accumulated, flush it to the text part
        flushTextBuilder()
        contentParts.add(part)
    }

    /**
     * Adds [MessagePart.Text] to the list of parts.
     */
    public fun text(text: String, cacheControl: CacheControl) {
        part(MessagePart.Text(text, cacheControl))
    }

    public fun text(cacheControl: CacheControl? = null, init: TextContentBuilder.() -> Unit) {
        part(MessagePart.Text(TextContentBuilder().apply(init).build(), cacheControl))
    }

    public fun attachment(attachment: AttachmentSource, cacheControl: CacheControl? = null) {
        part(MessagePart.Attachment(attachment, cacheControl))
    }

    /**
     * Adds [AttachmentSource.Image] to the list of parts.
     */
    public fun image(image: AttachmentSource.Image, cacheControl: CacheControl? = null) {
        attachment(image, cacheControl)
    }

    /**
     * Adds [AttachmentSource.Image] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Image URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun image(url: String, cacheControl: CacheControl? = null) {
        val fileData = url.urlFileData()
        image(
            AttachmentSource.Image(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.Image] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local image file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun image(path: Path, cacheControl: CacheControl? = null) {
        val fileData = path.fileData()
        image(
            AttachmentSource.Image(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.Audio] to the list of parts.
     */
    public fun audio(audio: AttachmentSource.Audio, cacheControl: CacheControl? = null) {
        attachment(audio, cacheControl)
    }

    /**
     * Adds [AttachmentSource.Audio] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Audio URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun audio(url: String, cacheControl: CacheControl? = null) {
        val fileData = url.urlFileData()
        audio(
            AttachmentSource.Audio(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.Audio] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local audio file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun audio(path: Path, cacheControl: CacheControl? = null) {
        val fileData = path.fileData()
        audio(
            AttachmentSource.Audio(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.Video] to the list of parts.
     */
    public fun video(video: AttachmentSource.Video, cacheControl: CacheControl? = null) {
        attachment(video, cacheControl)
    }

    /**
     * Adds [AttachmentSource.Video] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Video URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun video(url: String, cacheControl: CacheControl? = null) {
        val fileData = url.urlFileData()
        video(
            AttachmentSource.Video(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.Video] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local video file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun video(path: Path, cacheControl: CacheControl? = null) {
        val fileData = path.fileData()
        video(
            AttachmentSource.Video(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.File] to the list of parts.
     */
    public fun file(file: AttachmentSource.File, cacheControl: CacheControl? = null) {
        attachment(file, cacheControl)
    }

    /**
     * Adds [AttachmentSource.File] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url File URL
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun file(url: String, mimeType: String, cacheControl: CacheControl? = null) {
        val fileData = url.urlFileData()
        file(
            AttachmentSource.File(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.File] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local file
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun binaryFile(path: Path, mimeType: String, cacheControl: CacheControl? = null) {
        val fileData = path.fileData()
        file(
            AttachmentSource.File(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            ),
            cacheControl
        )
    }

    /**
     * Adds [AttachmentSource.File] with [AttachmentContent.PlainText] content from the provided local file path.
     *
     * @param path Path to local file
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun textFile(path: Path, mimeType: String, cacheControl: CacheControl? = null) {
        val fileData = path.fileData()
        file(
            AttachmentSource.File(
                content = AttachmentContent.PlainText(path.readText()),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            ),
            cacheControl
        )
    }
}
