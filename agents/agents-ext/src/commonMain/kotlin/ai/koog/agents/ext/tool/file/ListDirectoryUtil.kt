package ai.koog.agents.ext.tool.file

import ai.koog.agents.ext.tool.file.render.norm
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.filter.GlobPattern
import ai.koog.rag.base.files.model.FileSystemEntry
import ai.koog.rag.base.files.model.buildFileEntry
import ai.koog.rag.base.files.model.buildFolderEntry
import kotlin.collections.plusAssign

/**
 * Builds a directory tree with support for glob patterns.
 *
 * - Files are matched by their normalized relative path.
 * - Collapses chains of single-child directories (depth preserved).
 * - Skips non-matching entries if a filter is provided.
 */
internal suspend fun <Path> buildDirectoryTree(
    fs: FileSystemProvider.ReadOnly<Path>,
    start: Path,
    startMetadata: FileMetadata,
    maxDepth: Int,
    filter: GlobPattern? = null
): FileSystemEntry? {
    require(maxDepth > 0) { "maxDepth must be > 0" }

    return buildNode(
        fs = fs,
        rootPath = start,
        currentPath = start,
        metadata = startMetadata,
        depth = maxDepth,
        filter = filter
    )
}

/**
 * Recursively builds a node in the directory tree.
 */
private suspend fun <Path> buildNode(
    fs: FileSystemProvider.ReadOnly<Path>,
    rootPath: Path,
    currentPath: Path,
    metadata: FileMetadata,
    depth: Int,
    filter: GlobPattern?
): FileSystemEntry? {
    if (metadata.type == FileMetadata.FileType.File) {
        return if (matchesFilter(fs, rootPath, currentPath, filter)) {
            buildFileEntry(fs, currentPath, metadata)
        } else {
            null
        }
    }

    val children = fs.list(currentPath).mapNotNull { child ->
        fs.metadata(child)?.let { child to it }
    }

    val visibleChildren = children.filter { (childPath, childMeta) ->
        childMeta.type == FileMetadata.FileType.Directory ||
            matchesFilter(fs, rootPath, childPath, filter)
    }

    val entries = mutableListOf<FileSystemEntry>()
    for ((childPath, childMeta) in visibleChildren) {
        when (childMeta.type) {
            FileMetadata.FileType.File -> {
                entries += buildFileEntry(fs, childPath, childMeta)
            }

            FileMetadata.FileType.Directory -> {
                // Next depth: if this folder has only one child directory, keep the depth the same (unwrap the chain)
                val nextDepth = if (visibleChildren.size == 1) depth else depth - 1

                if (nextDepth > 0) {
                    buildNode(fs, rootPath, childPath, childMeta, nextDepth, filter)
                        ?.let { entries += it }
                } else if (matchesFilter(fs, rootPath, childPath, filter)) {
                    entries += buildFolderEntry(fs, childPath, childMeta, entries = null)
                }
            }
        }
    }

    if (filter != null && entries.isEmpty()) {
        return null
    }

    return buildFolderEntry(fs, currentPath, metadata, entries)
}

/**
 * Converts a path to a normalized relative path string for glob matching.
 * Falls back to the filename if relativization fails.
 */
private fun <Path> getRelativePath(
    fs: FileSystemProvider.ReadOnly<Path>,
    rootPath: Path,
    path: Path
): String {
    return (fs.relativize(rootPath, path) ?: fs.name(path)).norm()
}

/**
 * Checks if a file path matches the filter pattern.
 * Always returns true if no filter is specified.
 */
private fun <Path> matchesFilter(
    fs: FileSystemProvider.ReadOnly<Path>,
    rootPath: Path,
    path: Path,
    filter: GlobPattern?
): Boolean {
    return filter == null || filter.matches(getRelativePath(fs, rootPath, path))
}
