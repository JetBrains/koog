package ai.koog.rag.base.files.model

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.readText

/**
 * Creates a [File] from the path and metadata without loading file content.
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to access file attributes
 * @param path the file path to describe
 * @param metadata metadata for the file at [path]
 * @return a [File] with metadata and no content ([Content.None])
 */
public suspend fun <Path> buildFileEntry(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    metadata: FileMetadata
): FileSystemEntry.File {
    val type = fs.getFileContentType(path)
    return FileSystemEntry.File(
        name = fs.name(path),
        extension = fs.extension(path),
        path = fs.toAbsolutePathString(path),
        hidden = metadata.hidden,
        size = buildFileSize(fs, path, type),
        contentType = type,
        content = FileSystemEntry.File.Content.None,
    )
}

/**
 * Creates a [Folder] from the path, metadata, and optional entries.
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to access directory attributes
 * @param path the directory path to describe
 * @param metadata metadata for the directory at [path]
 * @param entries child entries (files and folders), or null if not loaded
 * @return a [Folder] with metadata and optional children
 */
public fun <Path> buildFolderEntry(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    metadata: FileMetadata,
    entries: List<FileSystemEntry>?
): FileSystemEntry.Folder {
    return FileSystemEntry.Folder(
        name = fs.name(path),
        path = fs.toAbsolutePathString(path),
        hidden = metadata.hidden,
        entries = entries
    )
}

/**
 * Creates a [FileSystemEntry] (File or Folder) based on the metadata type.
 *
 * Decides whether to build a [File] or [Folder]
 * according to [FileMetadata.type]. Does not load file content; files will have
 * [Content.None].
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to access attributes
 * @param path the path to the file or directory
 * @param metadata metadata describing the entry at [path]
 * @return a [FileSystemEntry] corresponding to the provided [metadata]
 */
public suspend fun <Path> buildFileSystemEntry(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    metadata: FileMetadata,
): FileSystemEntry {
    return when (metadata.type) {
        FileMetadata.FileType.File -> {
            buildFileEntry(
                fs = fs,
                path = path,
                metadata = metadata
            )
        }

        FileMetadata.FileType.Directory -> {
            buildFolderEntry(
                fs = fs,
                path = path,
                metadata = metadata,
                entries = null
            )
        }
    }
}

/**
 * Creates [FileSize] representations for the given file.
 *
 * Always returns a [FileSize.Bytes] instance. For text files ≤ 1 MiB,
 * also returns a [FileSize.Lines] instance.
 * For files > 1 MiB or non-text files, only [FileSize.Bytes] is returned
 * to avoid loading large or unsupported content.
 *
 * @param Path the filesystem path type
 * @param fs the filesystem provider used to access the file
 * @param path the file path to measure
 * @param contentType optional file content type
 * @return a list containing [FileSize.Bytes] and, for small text files, [FileSize.Lines]
 */
public suspend fun <Path> buildFileSize(
    fs: FileSystemProvider.ReadOnly<Path>,
    path: Path,
    contentType: FileMetadata.FileContentType? = null,
): List<FileSize> {
    val bytes = FileSize.Bytes(fs.size(path))
    val type = contentType ?: fs.getFileContentType(path)

    if (bytes.bytes > FileSize.MIB || type != FileMetadata.FileContentType.Text) {
        return listOf(bytes)
    }

    return listOf(bytes, FileSize.Lines(fs.readText(path).lineSequence().count()))
}
