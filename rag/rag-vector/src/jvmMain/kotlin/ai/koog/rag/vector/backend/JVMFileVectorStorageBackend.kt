package ai.koog.rag.vector.backend

import ai.koog.rag.base.files.JVMDocumentProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.nio.file.Path

/**
 * A JVM-specific implementation of [FileVectorStorageBackend] for managing the storage of documents
 * and associated vector embeddings on a file system.
 *
 * This class utilizes a [ai.koog.rag.base.files.JVMDocumentProvider] along with a JVM-compatible [ai.koog.rag.base.files.FileSystemProvider.ReadWrite]
 * to handle document operations and vector storage in a structured directory format. It uses a
 * root directory as the base for storing documents and their associated embeddings in separate directories.
 *
 * Use this class to persistently store and retrieve documents and their vector payloads to and from
 * a file-based system in JVM environments.
 *
 * @constructor Initializes the [JVMFileVectorStorageBackend] with a specified root directory [root].
 * @param root The root directory where all documents and vector embeddings will be stored.
 */
public class JVMFileVectorStorageBackend(
    private val root: Path,
) : FileVectorStorageBackend<Path, Path>(JVMDocumentProvider, JVMFileSystemProvider.ReadWrite, root)
