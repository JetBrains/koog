package ai.koog.agents.ext.tool.file

import ai.koog.agents.ext.tool.file.model.FileSystemEntry.File
import ai.koog.agents.ext.tool.file.model.FileSystemEntry.File.Content
import ai.koog.agents.ext.tool.file.model.buildFileSize
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.readText

/**
 * Constructs a text file entry with content and metadata from the filesystem.
 *
 * Reads the file content and creates a [File] with the specified line range.
 * For full file content, pass `startLine = 0` and `endLine = -1`. The content will be
 * represented as either [Content.Text] for complete files or [Content.Excerpt] for partial ranges.
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to read file content and attributes
 * @param path the path to the file
 * @param metadata metadata for the file
 * @param startLine the starting line index (0-based, inclusive) for content extraction
 * @param endLine the ending line index (0-based, exclusive) for content extraction, or -1 for the end of the file
 * @return a [File] entry containing the requested content range and file attributes
 * @throws IllegalArgumentException if startLine < 0, endLine < -1, startLine >= lineCount,
 *         endLine <= startLine (when not -1), startLine >= lineCount, or endLine > lineCount
 */
internal suspend fun <Path> buildTextFileEntry(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    metadata: FileMetadata,
    startLine: Int,
    endLine: Int
): File {
    return File(
        name = fs.name(path),
        extension = fs.extension(path),
        path = fs.toAbsolutePathString(path),
        hidden = metadata.hidden,
        size = buildFileSize(fs, path, FileMetadata.FileContentType.Text),
        contentType = FileMetadata.FileContentType.Text,
        content = buildContent(fs.readText(path), startLine, endLine)
    )
}

private fun buildContent(
    content: String,
    startLine: Int,
    endLine: Int
): Content {
    require(startLine >= 0) { "startLine=$startLine must be >= 0" }
    val lineCount = content.lineSequence().count()
    require(startLine < lineCount) { "startLine=$startLine must be < lineCount=$lineCount" }

    require(endLine >= -1) { "endLine=$endLine must be >= -1" }
    require(endLine == -1 || endLine > startLine) { "endLine=$endLine must be > startLine=$startLine or -1" }
    require(endLine == -1 || endLine <= lineCount) { "endLine=$endLine must be <= lineCount=$lineCount or -1" }

    val endLine = if (endLine == -1) lineCount else endLine

    if (startLine == 0 && endLine == lineCount) {
        return Content.Text(content)
    }

    val start = DocumentProvider.Position(startLine, 0)
    val end = DocumentProvider.Position(endLine, 0)
    val range = DocumentProvider.DocumentRange(start, end)

    return Content.Excerpt(
        listOf(
            Content.Excerpt.Snippet(
                text = range.substring(content),
                range = range,
            )
        )
    )
}
