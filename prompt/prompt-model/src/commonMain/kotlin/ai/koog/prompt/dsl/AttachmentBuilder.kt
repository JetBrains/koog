package ai.koog.prompt.dsl

import ai.koog.prompt.message.Attachment
import ai.koog.prompt.message.AttachmentContent
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString

/**
 * A builder for constructing attachments for prompt messages.
 *
 * Example usage:
 * ```kotlin
 * val attachments = AttachmentBuilder().apply {
 *     image("screenshot.png")
 *     binaryFile("report.pdf")
 * }.build()
 * ```
 *
 * @see Attachment
 * @see MessageContentBuilder
 */
@PromptDSL
public class AttachmentBuilder {
    private val attachments = mutableListOf<Attachment>()

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
     * Adds [Attachment] to the list of attachments.
     */
    public fun attachment(attachment: Attachment) {
        attachments.add(attachment)
    }

    /**
     * Adds [Attachment.Image] to the list of attachments.
     */
    public fun image(image: Attachment.Image) {
        attachment(image)
    }

    /**
     * Adds [Attachment.Image] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Image URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun image(url: String) {
        val fileData = url.urlFileData()
        image(
            Attachment.Image(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.Image] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local image file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun image(path: Path) {
        val fileData = path.fileData()
        image(
            Attachment.Image(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.Audio] to the list of attachments.
     */
    public fun audio(audio: Attachment.Audio) {
        attachments.add(audio)
    }

    /**
     * Adds [Attachment.Audio] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Audio URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun audio(url: String) {
        val fileData = url.urlFileData()
        audio(
            Attachment.Audio(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.Audio] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local audio file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun audio(path: Path) {
        val fileData = path.fileData()
        audio(
            Attachment.Audio(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.Video] to the list of attachments.
     */
    public fun video(video: Attachment.Video) {
        attachments.add(video)
    }

    /**
     * Adds [Attachment.Video] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url Video URL
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun video(url: String) {
        val fileData = url.urlFileData()
        video(
            Attachment.Video(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.Video] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local video file
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun video(path: Path) {
        val fileData = path.fileData()
        video(
            Attachment.Video(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.File] to the list of attachments.
     */
    public fun file(file: Attachment.File) {
        attachments.add(file)
    }

    /**
     * Adds [Attachment.File] with [AttachmentContent.URL] content from the provided URL.
     *
     * @param url File URL
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the URL is not valid or no file in the URL was found.
     */
    public fun file(url: String, mimeType: String) {
        val fileData = url.urlFileData()
        file(
            Attachment.File(
                content = AttachmentContent.URL(url),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.File] with [AttachmentContent.Binary.Bytes] content from the provided local file path.
     *
     * @param path Path to local file
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun binaryFile(path: Path, mimeType: String) {
        val fileData = path.fileData()
        file(
            Attachment.File(
                content = AttachmentContent.Binary.Bytes(path.readByteArray()),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            )
        )
    }

    /**
     * Adds [Attachment.File] with [AttachmentContent.PlainText] content from the provided local file path.
     *
     * @param path Path to local file
     * @param mimeType MIME type of the file (e.g., "application/pdf", "text/plain")
     * @throws IllegalArgumentException if the path is not valid, the file does not exist, or is not a regular file.
     */
    public fun textFile(path: Path, mimeType: String) {
        val fileData = path.fileData()
        file(
            Attachment.File(
                content = AttachmentContent.PlainText(path.readText()),
                format = fileData.extension,
                mimeType = mimeType,
                fileName = fileData.name
            )
        )
    }

    /**
     * Constructs and returns the accumulated list of attachment items.
     *
     * @return A list containing all the attachment items created through the builder methods
     */
    public fun build(): List<Attachment> = attachments
}
