package ai.koog.agents.ext.tool.file

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.model.FileSystemEntry
import ai.koog.rag.base.files.model.buildFileSize
import ai.koog.rag.base.files.readText

/**
 * Constructs a text file entry with content and metadata from the filesystem.
 *
 * Reads the file content and creates a [File] with the specified line range.
 * For full file content, pass `startLine = 0` and `endLine = -1`. The content will be
 * represented as either [Content.Text] for complete files or [Content.Excerpt] for partial ranges.
 *
 * When `endLine` exceeds the file's line count, the range is automatically clamped to available lines
 * and the callback is invoked to notify the caller.
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to read file content and attributes
 * @param path the path to the file
 * @param metadata metadata for the file
 * @param startLine the starting line index (0-based, inclusive) for content extraction
 * @param endLine the ending line index (0-based, exclusive) for content extraction, or -1 for the end of the file
 * @param onEndLineExceedsFileLength callback invoked when endLine exceeds the file's line count, receives (endLine, fileLineCount)
 * @return a [File] entry with the requested content range (endLine clamped to file length if needed)
 * @throws IllegalArgumentException if startLine < 0, endLine < -1, startLine >= lineCount, or endLine <= startLine (when not -1)
 */
internal suspend fun <Path> buildTextFileEntry(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    metadata: FileMetadata,
    startLine: Int,
    endLine: Int,
    onEndLineExceedsFileLength: ((endLine: Int, fileLineCount: Int) -> Unit)? = null
): FileSystemEntry.File {
    return FileSystemEntry.File(
        name = fs.name(path),
        extension = fs.extension(path),
        path = fs.toAbsolutePathString(path),
        hidden = metadata.hidden,
        size = buildFileSize(fs, path, FileMetadata.FileContentType.Text),
        contentType = FileMetadata.FileContentType.Text,
        content = buildContent(fs.readText(path), startLine, endLine, onEndLineExceedsFileLength)
    )
}

private fun buildContent(
    content: String,
    startLine: Int,
    endLine: Int,
    onEndLineExceedsFileLength: ((requestedEndLine: Int, fileLineCount: Int) -> Unit)?
): FileSystemEntry.File.Content {
    val lineCount = content.lineSequence().count()

    require(startLine >= 0) { "startLine=$startLine must be >= 0" }
    require(startLine < lineCount) { "startLine=$startLine must be < lineCount=$lineCount" }

    require(endLine >= -1) { "endLine=$endLine must be >= -1" }
    require(endLine == -1 || endLine > startLine) { "endLine=$endLine must be > startLine=$startLine or -1" }

    val clampedEndLine = if (endLine == -1) lineCount else minOf(endLine, lineCount)

    if (endLine != -1 && endLine > lineCount) {
        onEndLineExceedsFileLength?.invoke(endLine, lineCount)
    }

    if (startLine == 0 && clampedEndLine == lineCount) {
        return FileSystemEntry.File.Content.Text(content)
    }

    val range = DocumentProvider.DocumentRange(
        DocumentProvider.Position(startLine, 0),
        DocumentProvider.Position(clampedEndLine, 0)
    )

    return FileSystemEntry.File.Content.Excerpt(
        listOf(
            FileSystemEntry.File.Content.Excerpt.Snippet(
                text = range.substring(content),
                range = range,
            )
        )
    )
}
