package ai.koog.agents.ext.utils

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Minimal in-memory filesystem for tests. Implements ReadWrite<String> and stores text as ByteArray.
 */
class InMemoryFS : FileSystemProvider.ReadWrite<String> {
    private val files = mutableMapOf<String, ByteArray>()
    private val directories = mutableSetOf<String>()

    override fun fromAbsolutePathString(path: String): String = path
    override fun toAbsolutePathString(path: String): String = path
    override fun joinPath(base: String, vararg parts: String): String = (sequenceOf(base) + parts.asSequence()).joinToString("/")
    override fun name(path: String): String = path.substringAfterLast('/')
    override fun parent(path: String): String? = path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { null }
    override fun extension(path: String): String = name(path).substringAfterLast('.', "")

    override suspend fun exists(path: String): Boolean = files.containsKey(path) || directories.contains(path)
    override suspend fun metadata(path: String): FileMetadata? = when {
        files.containsKey(path) -> FileMetadata(FileMetadata.FileType.File, hidden = false)
        directories.contains(path) -> FileMetadata(FileMetadata.FileType.Directory, hidden = false)
        else -> null
    }
    override suspend fun size(path: String): Long =
        files[path]?.size?.toLong() ?: throw IOException("No such file: $path")

    override suspend fun readBytes(path: String): ByteArray = files[path] ?: throw IOException("No such file: $path")
    override suspend fun writeBytes(path: String, data: ByteArray) {
        parent(path)?.let { directories.add(it) }
        files[path] = data
    }

    override suspend fun inputStream(path: String): Source = throw UnsupportedOperationException("Not used in tests")
    override suspend fun outputStream(path: String, append: Boolean): Sink = throw UnsupportedOperationException("Not used in tests")

    override suspend fun create(path: String, type: FileMetadata.FileType) {
        require(exists(parent(path) ?: "")) { "Parent directory does not exist: $path" }
        require(!exists(path)) { "File already exists: $path" }
        when (type) {
            FileMetadata.FileType.File -> if (!files.containsKey(path)) files[path] = ByteArray(0)
            FileMetadata.FileType.Directory -> directories.add(path)
        }
    }
    override suspend fun delete(path: String) {
        require(exists(path)) { "File does not exist: $path" }
        files.remove(path)
        directories.remove(path)
    }
    override suspend fun move(source: String, target: String) {
        require(exists(source)) { "File does not exist: $source" }
        require(!exists(target)) { "File already exists: $target" }
        val data = files.remove(source)
        if (data != null) files[target] = data else throw IOException("No such file: $source")
    }
    override suspend fun copy(source: String, target: String) {
        require(exists(source)) { "File does not exist: $source" }
        require(!exists(target)) { "File already exists: $target" }
        val data = files[source] ?: throw IOException("No such file: $source")
        files[target] = data.copyOf()
    }
    override suspend fun list(directory: String): List<String> {
        require(exists(directory)) { "Directory does not exist: $directory" }
        require(metadata(directory)?.type == FileMetadata.FileType.Directory) { "Not a directory: $directory" }

        val children = mutableSetOf<String>()
        for (filePath in files.keys) {
            if (parent(filePath) == directory) {
                children.add(filePath)
            }
        }
        for (dirPath in directories) {
            if (parent(dirPath) == directory) {
                children.add(dirPath)
            }
        }
        return children.sorted()
    }

    override fun relativize(root: String, path: String): String = path.removePrefix(if (root.endsWith("/")) root else "$root/")
    override suspend fun getFileContentType(path: String): FileMetadata.FileContentType {
        require(exists(path)) { "File does not exist: $path" }
        return FileMetadata.FileContentType.Text
    }
}
