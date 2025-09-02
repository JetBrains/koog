package ai.koog.test.utils

import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source

/**
 * Minimal in-memory filesystem for tests. Implements ReadWrite<String> and stores text as ByteArray.
 * Shared across integration tests to avoid duplication.
 */
public class InMemoryFS : FileSystemProvider.ReadWrite<String> {
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
        when (type) {
            FileMetadata.FileType.File -> if (!files.containsKey(path)) files[path] = ByteArray(0)
            FileMetadata.FileType.Directory -> directories.add(path)
        }
    }
    override suspend fun delete(path: String) {
        files.remove(path)
        directories.remove(path)
    }
    override suspend fun move(source: String, target: String) {
        val data = files.remove(source)
        if (data != null) files[target] = data else throw IOException("No such file: $source")
    }
    override suspend fun copy(source: String, target: String) {
        val data = files[source] ?: throw IOException("No such file: $source")
        files[target] = data.copyOf()
    }
    override suspend fun list(directory: String): List<String> =
        files.keys.filter { it.startsWith(if (directory.endsWith("/")) directory else "$directory/") }
    override fun relativize(root: String, path: String): String = path.removePrefix(if (root.endsWith("/")) root else "$root/")
    override suspend fun getFileContentType(path: String): FileMetadata.FileContentType = FileMetadata.FileContentType.Text
}
