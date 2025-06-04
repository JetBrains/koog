@file:OptIn(ExperimentalEncodingApi::class)

package ai.koog.prompt.message

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.jvm.JvmInline

/**
 * Represents different types of media content that can be attached to messages.
 * Supports images, videos, audio files, and documents with base64 encoding capabilities.
 */
@Serializable
public sealed class MediaContent {
    /**
     * A regular expression to validate and match URLs that start with "http" or "https".
     * It is case-insensitive and is used to ensure the format of URLs in the media content.
     */
    @Transient
    protected val urlRegex: Regex = """^https?://.*""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * The file format/extension of the media content.
     */
    public abstract val format: String?

    /**
     * Retrieves the MIME type of the media content.
     *
     * @return A string representing the MIME type.
     */
    public abstract fun getMimeType(): String

    /**
     * Converts the media content to base64 encoded string.
     * @return Base64 encoded representation of the content.
     */
    public abstract fun toBase64(): String

    /**
     * Represents image content that can be loaded from a local file or URL.
     * @property source The path to local file or URL of the image.
     */
    @Serializable
    public data class Image(val source: String) : MediaContent() {
        override val format: String? by lazy {
            source
                .substringBeforeLast("?")
                .substringBeforeLast("#")
                .substringAfterLast(".", "")
                .takeIf { it.isNotEmpty() }
                ?.lowercase()
        }

        private val imageSource: FileSource by lazy {
            when {
                source.matches(urlRegex) -> FileSource.Url(source)
                else -> FileSource.LocalPath(source)
            }
        }

        /**
         * Checks if the image source is a URL.
         * @return true if source is a URL, false if it's a local path.
         */
        public fun isUrl(): Boolean = imageSource is FileSource.Url

        /**
         * Gets the MIME type based on the image format.
         * @return MIME type string for the image format.
         */
        public override fun getMimeType(): String = when (format) {
            "png" -> "image/png"
            "jpeg", "jpg" -> "image/jpeg"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "gif" -> "image/gif"
            else -> error("Unsupported mimeType for file format: $format")
        }

        public override fun toBase64(): String = when (val src = imageSource) {
            is FileSource.Url -> error("Cannot encode URL to base64. Download the image first.")
            is FileSource.LocalPath -> src.encodeLocalFile()
        }
    }

    /**
     * Represents video content stored as byte array.
     * @property data The video data as byte array.
     * @property format The video file format.
     */
    @Serializable
    public class Video(public val data: ByteArray, public override val format: String) : MediaContent() {
        public override fun getMimeType(): String = "video/$format"

        override fun toBase64(): String = Base64.encode(data)
    }

    /**
     * Represents audio content stored as byte array.
     * @property data The audio data as byte array.
     * @property format The audio file format.
     */
    @Serializable
    public class Audio(public val data: ByteArray, public override val format: String) : MediaContent() {
        public override fun getMimeType(): String = "audio/$format"

        public override fun toBase64(): String = Base64.encode(data)
    }

    /**
     * Represents a document file that can be loaded from a local path.
     * URLs are not supported for security reasons.
     * @property source The local file path.
     */
    @Serializable
    public data class File(val source: String) : MediaContent() {
        override val format: String? by lazy {
            source
                .substringBeforeLast("?")
                .substringBeforeLast("#")
                .substringAfterLast(".", "")
                .takeIf { it.isNotEmpty() }
                ?.lowercase()
        }

        private val fileSource: FileSource by lazy {
            when {
                source.matches(urlRegex) -> FileSource.Url(source)
                else -> FileSource.LocalPath(source)
            }
        }

        /**
         * Checks if the file source is a URL.
         * @return true if the source is a URL, false if it's a local path.
         */
        public fun isUrl(): Boolean = fileSource is FileSource.Url

        /**
         * Gets the MIME type based on the file format.
         * @return MIME type string for the file format.
         */
        public override fun getMimeType(): String = when (format) {
            "pdf" -> "application/pdf"
            "js" -> "application/x-javascript"
            "py" -> "application/x-python"
            "txt" -> "text/plain"
            "html" -> "text/html"
            "css" -> "text/css"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "xml" -> "text/xml"
            "rtf" -> "text/rtf"
            else -> error("Unsupported mimeType for file format: $format")
        }

        /**
         * Gets the file name from the source path.
         * @return The file name extracted from the path.
         */
        public fun fileName(): String = when (val src = fileSource) {
            is FileSource.LocalPath -> src.value.substringAfterLast("/")
            is FileSource.Url -> error("Cannot get fileName for URL. Download the file first.")
        }

        /**
         * Reads the content of a file based on its source type.
         * If the file source is a local path, the file content is read and returned as a string.
         * If the file source is a URL, an error is thrown.
         *
         * @return The text content of the file if the source is a local path.
         * @throws IllegalStateException if the source is a URL, as reading directly from a URL is not supported.
         */
        public fun readText(): String = when (val src = fileSource) {
            is FileSource.LocalPath -> src.readText()
            is FileSource.Url -> error("Cannot read file from URL. Download the file first.")
        }
        /**
         * Converts the file content to a Base64 encoded string.
         * This method supports encoding for files with a local path source.
         * For URL sources, an exception is thrown since encoding is not supported directly.
         *
         * @return Base64 encoded string representation of the file content if the file source is a local path.
         * @throws IllegalStateException if the file source is a URL as encoding is not supported.
         */
        public override fun toBase64(): String = when (val src = fileSource) {
            is FileSource.LocalPath -> src.encodeLocalFile()
            is FileSource.Url -> error("Cannot encode URL to base64. Download the file first.")
        }
    }
}

/**
 * Internal representation of file sources (URL or local path).
 */
private sealed interface FileSource {
    /**
     * Represents a URL source.
     */
    @JvmInline
    value class Url(val value: String) : FileSource

    /**
     * Represents a local file path source.
     */
    class LocalPath(val value: String) : FileSource {

        private val path: Path by lazy { Path(value) }

        /**
         * Reads the content of a file located at the specified path as a string.
         *
         * @return The content of the file as a string.
         * @throws IllegalArgumentException If the specified path is not a regular file.
         * @throws IllegalStateException If the file is not found.
         */
        fun readText(): String {
            val metadata = requireNotNull(SystemFileSystem.metadataOrNull(path)) {
                "File not found: $path"
            }
            require(metadata.isRegularFile) {
                "Path is not a regular file: $path"
            }

            return SystemFileSystem.source(path).buffered().use {
                it.readString()
            }
        }

        /**
         * Encodes the local file content to base64.
         * @return Base64 encoded file content.
         */
        fun encodeLocalFile(): String {
            val metadata = requireNotNull(SystemFileSystem.metadataOrNull(path)) {
                "File not found: $path"
            }
            require(metadata.isRegularFile) {
                "Path is not a regular file: $path"
            }

            return SystemFileSystem.source(path).buffered().use {
                Base64.encode(it.readByteArray())
            }
        }
    }
}