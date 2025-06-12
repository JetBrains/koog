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

class MockFileSystemProvicer(val mockFileSystem: MockFileSystem) : FileSystemProvider.ReadWrite<String> {
    override fun toPathString(path: String): String = path

    override fun toAbsolutePathString(path: String): String = path

    override fun fromAbsoluteString(path: String): String = path

    override fun fromRelativeString(base: String, path: String): String = "$base/$path"

    override suspend fun name(path: String): String = path.substringAfterLast('/')

    override suspend fun extension(path: String): String = path.substringAfterLast('.')

    override suspend fun metadata(path: String): FileMetadata? = null

    override suspend fun list(path: String): List<String> {
        return mockFileSystem.documents
            .filter { (docPath, entry) ->
                docPath.startsWith(path.removeSuffix("/")) && entry is MockDocument
            }
            .keys.toList()
    }

    override suspend fun parent(path: String): String? = path.substringBeforeLast('/', "").ifEmpty { null }

    override suspend fun relativize(root: String, path: String): String? =
        if (path.startsWith(root)) path.removePrefix(root) else null

    override suspend fun exists(path: String): Boolean = path in mockFileSystem.documents

    override suspend fun read(path: String): ByteArray =
        mockFileSystem.documents[path]?.let { it as? MockDocument }?.content?.encodeToByteArray()
            ?: throw IllegalArgumentException("Document not found: $path")

    override suspend fun source(path: String): Source = throw UnsupportedOperationException()

    override suspend fun size(path: String): Long =
        mockFileSystem.documents[path]?.let { it as? MockDocument }?.content?.length?.toLong() ?: 0L

    override suspend fun create(
        parent: String,
        name: String,
        type: FileMetadata.FileType
    ) {
        when (type) {
            FileMetadata.FileType.File -> mockFileSystem.saveDocument(fromRelativeString(parent, name), "")
            FileMetadata.FileType.Directory -> mockFileSystem.createDirectory(fromRelativeString(parent, name))
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

    override suspend fun write(path: String, content: ByteArray) {
        mockFileSystem.saveDocument(path, content.decodeToString())
    }

    override suspend fun sink(path: String, append: Boolean): Sink = throw UnsupportedOperationException()

    override suspend fun delete(parent: String, name: String) {
        mockFileSystem.deleteDocument(fromRelativeString(parent, name))
    }
}