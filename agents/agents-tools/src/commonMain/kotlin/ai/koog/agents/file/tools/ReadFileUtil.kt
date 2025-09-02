package ai.koog.agents.file.tools

import ai.koog.agents.file.tools.model.FileSize
import ai.koog.agents.file.tools.model.FileSize.Bytes
import ai.koog.agents.file.tools.model.FileSize.Companion.MIB
import ai.koog.agents.file.tools.model.FileSize.Lines
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.model.FileSystemEntry.File.Content
import ai.koog.agents.file.tools.model.FileSystemEntry.File.Content.Excerpt
import ai.koog.agents.file.tools.model.FileSystemEntry.File.Content.Text
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.readText

/**
 * Constructs a text file entry with content and metadata from the filesystem.
 *
 * Reads the file content and creates a [FileSystemEntry.File] with the specified line range.
 * For full file content, pass `startLine = 0` and `endLine = -1`. The content will be
 * represented as either [Text] for complete files or [Excerpt] for partial ranges.
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to read file content and attributes
 * @param path the absolute path to the file
 * @param metadata the pre-fetched metadata for the file at [path]
 * @param startLine the starting line index (0-based, inclusive) for content extraction
 * @param endLine the ending line index (0-based, exclusive) for content extraction, or -1 for the end of the file
 * @return a file entry containing the requested content range and file attributes
 * @throws IllegalArgumentException when contentType is not [FileMetadata.FileContentType.Text]
 */
public suspend fun <Path> buildTextFileEntry(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    metadata: FileMetadata,
    startLine: Int,
    endLine: Int
): FileSystemEntry.File {
    val name = fs.name(path)
    return FileSystemEntry.File(
        name = name,
        extension = fs.extension(path),
        path = fs.toAbsolutePathString(path),
        content = buildContent(
            fs.readText(path),
            startLine,
            endLine
        ),
        size = buildFileSize(fs, path),
        hidden = metadata.hidden,
        contentType = FileMetadata.FileContentType.Text,
    )
}

/**
 * Creates [Content] from a line range in a text.
 *
 * - Lines are 0-based. The `endLine` is exclusive, and `-1` means end of the file.
 * - Returns [Text] if the range spans the whole file.
 * - Returns [Excerpt] with one snippet otherwise.
 *
 * @param content full file text
 * @param startLine first line to include (0-based, inclusive)
 * @param endLine first line to exclude (0-based, exclusive), or -1 for the end of the file
 * @return [Text] when the whole file is selected, otherwise [Excerpt]
 * @throws IllegalArgumentException if `startLine < 0`, `endLine < -1`,
 *   (`endLine != -1` and `endLine <= startLine`), or startLine > fileLinesCount
 */
internal fun buildContent(
    content: String,
    startLine: Int,
    endLine: Int,
): Content {
    require(startLine >= 0) { "startLine must be >= 0: $startLine" }
    require(endLine >= -1) { "endLine must be >= -1: $endLine" }
    require(endLine == -1 || endLine > startLine) {
        "endLine must be > startLine or -1: startLine=$startLine, endLine=$endLine"
    }
    val fileLinesCount = content.lines().size
    require(startLine < fileLinesCount) { "startLine=$startLine must be strictly smaller than the whole fileLinesCount=$fileLinesCount" }

    val endLine = if (endLine == -1) fileLinesCount else endLine.coerceAtMost(fileLinesCount)

    if (startLine == 0 && endLine >= fileLinesCount) return Text(content)

    val start = DocumentProvider.Position(startLine, 0)
    val end = DocumentProvider.Position(endLine, 0)
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

/**
 * Creates [FileSize] representations for the given file.
 *
 * Always returns a [Bytes] instance. For text files ≤ 1 MiB, also returns a [Lines] instance.
 * For files > 1 MiB or non-text files, only [Bytes] is returned to avoid loading large or
 * unsupported content.
 *
 * @param Path the filesystem path type
 * @param path the file path to measure
 * @param fs the filesystem provider used to access the file
 * @return a list containing at least a [Bytes] instance and optionally a [Lines] instance
 */
public suspend fun <Path> buildFileSize(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path
): List<FileSize> {
    val bytes = Bytes(fs.size(path))
    if (bytes.bytes > MIB || fs.getFileContentType(path) != FileMetadata.FileContentType.Text) {
        return listOf(bytes)
    }

    val text = fs.readText(path)
    val lineCount = if (text.isBlank()) {
        0
    } else {
        val newlines = text.count { it == '\n' }
        newlines + if (text.last() != '\n') 1 else 0
    }
    return listOf(bytes, Lines(lineCount))
}
