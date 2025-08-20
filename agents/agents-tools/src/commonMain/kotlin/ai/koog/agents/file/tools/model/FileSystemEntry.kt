package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Provides a common interface for files and directories in a filesystem.
 *
 * @property name filename or directory name
 * @property extension file extension without dot, or `null` for directories
 * @property path complete filesystem path
 * @property hidden whether this entry is hidden
 */
@Serializable
public sealed interface FileSystemEntry {
    public val name: String
    public val extension: String?
    public val path: String
    public val hidden: Boolean

    /**
     * Visits this entry and its descendants in depth-first order.
     *
     * @param depth maximum depth to traverse; 0 visits only this entry
     * @param visitor function called for each visited entry
     */
    public suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit)

    /**
     * Represents a file in the filesystem.
     *
     * @property size list of [FileSize] measurements for this file
     * @property contentType file content type from [FileMetadata.FileContentType]
     * @property content file content, defaults to [Content.None]
     */
    @Serializable
    public data class File(
        override val name: String,
        override val extension: String?,
        override val path: String,
        override val hidden: Boolean,
        public val size: List<FileSize>,
        @SerialName("content_type") public val contentType: FileMetadata.FileContentType,
        public val content: Content = Content.None,
    ) : FileSystemEntry {
        /**
         * Visits this file by calling [visitor] once.
         *
         * @param depth ignored for files
         * @param visitor function called with this file
         */
        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
        }

        /**
         * Represents file content as none, full text, or excerpt.
         */
        @Serializable
        public sealed interface Content {
            /**
             * Represents no file content.
             */
            @Serializable
            public data object None : Content

            /**
             * Represents full file content.
             *
             * @property text complete file text
             */
            @Serializable
            public data class Text(val text: String) : Content

            /**
             * Represents multiple separate text selections from a file.
             *
             * Each snippet contains text from a different part of the file, allowing
             * non-contiguous selections.
             *
             * @property snippets text selections with their file positions
             */
            @Serializable
            public data class Excerpt(val snippets: List<Snippet>) : Content {
                /**
                 * Creates an [Excerpt] from multiple [Snippet]s.
                 *
                 * @param snippets the snippets to include
                 */
                public constructor(vararg snippets: Snippet) : this(snippets.toList())

                /**
                 * Represents a text selection with its location in the source file.
                 *
                 * @property text the selected text
                 * @property range position in the file (zero-based, start inclusive, end exclusive)
                 */
                @Serializable
                public data class Snippet(
                    val text: String,
                    val range: DocumentProvider.DocumentRange,
                )
            }

            public companion object {
                /**
                 * Creates file content from a line range.
                 *
                 * @param content file text to extract from
                 * @param startLine first line to include (0-based, inclusive)
                 * @param endLine first line to exclude (0-based, exclusive) or -1 for the end of the file
                 * @return [Text] if range covers the entire file, otherwise [Excerpt] with single snippet
                 * @throws IllegalArgumentException if startLine < 0, endLine < -1, or endLine <= startLine when endLine != -1
                 */
                public fun of(content: String, startLine: Int, endLine: Int): Content {
                    require(startLine >= 0) { "startLine must be >= 0: $startLine" }
                    require(endLine >= -1) { "endLine must be >= -1: $endLine" }
                    require(endLine == -1 || endLine > startLine) {
                        "endLine must be > startLine or -1: startLine=$startLine, endLine=$endLine"
                    }

                    val lines = content.lines()
                    val startIndex = startLine.coerceAtLeast(0)
                    val endIndex = if (endLine == -1) lines.size else endLine.coerceAtMost(lines.size)

                    if (startIndex == 0 && endIndex >= lines.size) {
                        return Text(content)
                    }

                    val start = DocumentProvider.Position(startIndex, 0)
                    val end = DocumentProvider.Position(endIndex, 0)
                    val range = DocumentProvider.DocumentRange(start, end)

                    return Excerpt(
                        listOf(
                            Excerpt.Snippet(
                                text = range.substring(content),
                                range = range,
                            )
                        )
                    )
                }
            }
        }

        public companion object {
            /**
             * Creates a [File] from a filesystem path.
             *
             * @param Path the provider's path type
             * @param path the file path to examine
             * @param content the file content, defaults to [Content.None]
             * @param fs the filesystem provider
             * @return [File] if the path exists and is a file, otherwise null
             * @throws kotlinx.io.IOException if I/O error occurs while accessing filesystem
             */
            public suspend fun <Path> of(
                path: Path,
                content: Content = Content.None,
                fs: FileSystemProvider.ReadOnly<Path>,
            ): File? {
                val metadata = fs.metadata(path) ?: return null
                if (metadata.type != FileMetadata.FileType.File) return null
                return File(
                    name = fs.name(path),
                    extension = fs.extension(path).takeIf { it.isNotEmpty() },
                    path = fs.toAbsolutePathString(path),
                    hidden = metadata.hidden,
                    contentType = fs.getFileContentType(path),
                    size = FileSize.of(path, fs),
                    content = content,
                )
            }
        }
    }

    /**
     * Represents a directory in the filesystem.
     *
     * @property entries child files and directories, or null if not loaded
     */
    @Serializable
    public data class Folder(
        override val name: String,
        override val path: String,
        override val hidden: Boolean,
        val entries: List<FileSystemEntry>? = null,
    ) : FileSystemEntry {
        /** Always null since directories have no file extensions. */
        override val extension: String? = null

        /**
         * Visits this folder and its descendants.
         *
         * @param depth maximum recursion depth
         * @param visitor function called for each visited [FileSystemEntry]
         */
        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
            if (depth == 0) return
            entries?.forEach { it.visit(depth - 1, visitor) }
        }

        public companion object {
            /**
             * Creates a [Folder] from a filesystem path.
             *
             * @param Path the provider's path type
             * @param path the directory path
             * @param entries child entries for this folder
             * @param fs the filesystem provider
             * @return [Folder] if the path exists and is a directory, otherwise null
             * @throws kotlinx.io.IOException if I/O error occurs while accessing filesystem
             */
            public suspend fun <Path> of(
                path: Path,
                entries: List<FileSystemEntry>? = null,
                fs: FileSystemProvider.ReadOnly<Path>,
            ): Folder? {
                val metadata = fs.metadata(path) ?: return null
                if (metadata.type != FileMetadata.FileType.Directory) return null
                return Folder(
                    name = fs.name(path),
                    path = fs.toAbsolutePathString(path),
                    hidden = metadata.hidden,
                    entries = entries,
                )
            }
        }
    }

    public companion object {
        /**
         * Creates the appropriate [FileSystemEntry] subtype from a path.
         *
         * @param Path the provider's path type
         * @param path the filesystem path to examine
         * @param fs the filesystem provider
         * @return [File] for files, [Folder] for directories, or null if the path does not exist
         * @throws kotlinx.io.IOException if I/O error occurs while accessing the filesystem
         */
        public suspend fun <Path> of(
            path: Path,
            fs: FileSystemProvider.ReadOnly<Path>,
        ): FileSystemEntry? {
            val metadata = fs.metadata(path) ?: return null
            return when (metadata.type) {
                FileMetadata.FileType.File ->
                    File(
                        name = fs.name(path),
                        extension = fs.extension(path).takeIf { it.isNotEmpty() },
                        path = fs.toAbsolutePathString(path),
                        hidden = metadata.hidden,
                        contentType = fs.getFileContentType(path),
                        size = FileSize.of(path, fs),
                    )

                FileMetadata.FileType.Directory ->
                    Folder(
                        name = fs.name(path),
                        path = fs.toAbsolutePathString(path),
                        hidden = metadata.hidden,
                    )
            }
        }
    }
}
