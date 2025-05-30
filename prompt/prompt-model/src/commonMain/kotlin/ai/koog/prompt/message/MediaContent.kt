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
    @Transient
    protected val urlRegex: Regex = """^https?://.*""".toRegex(RegexOption.IGNORE_CASE)

    /**
     * The file format/extension of the media content.
     */
    public abstract val format: String?

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
        public fun getMimeType(): String = when (format) {
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
        override fun toBase64(): String = Base64.encode(data)
    }

    /**
     * Represents audio content stored as byte array.
     * @property data The audio data as byte array.
     * @property format The audio file format.
     */
    @Serializable
    public class Audio(public val data: ByteArray, public override val format: String) : MediaContent() {
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
        public fun getMimeType(): String = when (format) {
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

        public fun readText(): String = when (val src = fileSource) {
            is FileSource.LocalPath -> src.readText()
            is FileSource.Url -> error("Cannot read file from URL. Download the file first.")
        }
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