package ai.koog.rag.vector.mocks

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.io.Sink
import kotlinx.io.Source

sealed interface MockFSEntry

data class MockDocument(val content: String) : MockFSEntry

data class MockDirectory(val path: String) : MockFSEntry

class MockFileSystem {
    val documents = mutableMapOf<String, MockFSEntry>()

    fun saveDocument(path: String, content: String) {
        documents[path] = MockDocument(content)
    }

    fun createDirectory(path: String) {
        documents[path] = MockDirectory(path)
    }

    fun readDocument(path: String): MockDocument? {
        return documents[path] as? MockDocument
    }

    fun deleteDocument(path: String): Boolean {
        return documents.remove(path) != null
    }
}

class MockDocumentProvider(val mockFileSystem: MockFileSystem) : DocumentProvider<String, MockDocument> {
    override suspend fun document(path: String): MockDocument {
        return mockFileSystem.readDocument(path) ?: throw IllegalArgumentException("Document not found: $path")
    }

    override suspend fun text(document: MockDocument): CharSequence {
        return document.content
    }
}

class MockFileSystemProvider(val mockFileSystem: MockFileSystem) : FileSystemProvider.ReadWrite<String> {
    override fun toAbsolutePathString(path: String): String = path

    override fun fromAbsolutePathString(path: String): String = path

    override fun joinPath(base: String, vararg parts: String): String {
        return parts.fold(base) { acc, part -> "$acc/$part" }
    }

    override fun name(path: String): String = path.substringAfterLast('/')

    override fun extension(path: String): String = path.substringAfterLast('.')

    override suspend fun metadata(path: String): FileMetadata? = null

    override suspend fun list(directory: String): List<String> {
        return mockFileSystem.documents
            .filter { (docPath, entry) ->
                docPath.startsWith(directory.removeSuffix("/")) && entry is MockDocument
            }
            .keys.toList()
    }

    override fun parent(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }

    override fun relativize(root: String, path: String): String? =
        if (path.startsWith(root)) path.removePrefix(root) else null

    override suspend fun exists(path: String): Boolean = path in mockFileSystem.documents

    override suspend fun getFileContentType(path: String): FileMetadata.FileContentType =
        when (mockFileSystem.documents[path]) {
            is MockDocument -> FileMetadata.FileContentType.Text
            is MockDirectory -> throw IllegalArgumentException("Path must be a regular file")
            else -> throw IllegalArgumentException("Path must exist and be a regular file")
        }

    override suspend fun readBytes(path: String): ByteArray =
        mockFileSystem.documents[path]?.let { it as? MockDocument }?.content?.encodeToByteArray()
            ?: throw IllegalArgumentException("Document not found: $path")

    override suspend fun inputStream(path: String): Source = throw UnsupportedOperationException()

    override suspend fun size(path: String): Long =
        mockFileSystem.documents[path]?.let { it as? MockDocument }?.content?.length?.toLong() ?: 0L

    override suspend fun create(path: String, type: FileMetadata.FileType) {
        when (type) {
            FileMetadata.FileType.File -> mockFileSystem.saveDocument(path, "")
            FileMetadata.FileType.Directory -> mockFileSystem.createDirectory(path)
        }
    }

    override suspend fun move(source: String, target: String) {
        mockFileSystem.documents[source]?.let {
            mockFileSystem.documents.remove(source)
        }.also {
            when (it) {
                is MockDirectory -> mockFileSystem.createDirectory(target)
                is MockDocument -> mockFileSystem.saveDocument(target, it.content)
                else -> {}
            }
        }
    }

    override suspend fun copy(source: String, target: String) {
        mockFileSystem.documents[source]?.also {
            when (it) {
                is MockDirectory -> mockFileSystem.createDirectory(target)
                is MockDocument -> mockFileSystem.saveDocument(target, it.content)
            }
        }
    }

    override suspend fun writeBytes(path: String, data: ByteArray) {
        mockFileSystem.saveDocument(path, data.decodeToString())
    }

    override suspend fun outputStream(path: String, append: Boolean): Sink = throw UnsupportedOperationException()

    override suspend fun delete(path: String) {
        mockFileSystem.deleteDocument(path)
    }
}
