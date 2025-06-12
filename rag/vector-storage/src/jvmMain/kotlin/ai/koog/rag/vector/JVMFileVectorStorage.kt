package ai.koog.rag.vector

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.JVMDocumentProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.nio.file.Path

/**
 * A JVM-specific implementation of [FileVectorStorage] for managing the storage of documents
 * and associated vector embeddings on a file system.
 *
 * This class utilizes a [JVMDocumentProvider] along with a JVM-compatible [FileSystemProvider.ReadWrite]
 * to handle document operations and vector storage in a structured directory format. It uses a
 * root directory as the base for storing documents and their associated embeddings in separate directories.
 *
 * Use this class to persistently store and retrieve documents and their vector payloads to and from
 * a file-based system in JVM environments.
 *
 * @constructor Initializes the [JVMFileVectorStorage] with a specified root directory [root].
 * @param root The root directory where all documents and vector embeddings will be stored.
 */
public class JVMFileVectorStorage(
    private val root: Path,
) : FileVectorStorage<Path, Path>(JVMDocumentProvider, JVMFileSystemProvider.ReadWrite, root)

/**
 * A file-system-based storage implementation for managing and embedding documents represented by file paths.
 *
 * This class extends [EmbeddingBasedDocumentStorage] and is specialized for JVM-based systems where documents
 * are represented as file paths ([Path]). It combines a [DocumentEmbedder] for embedding the file content into vectors
 * and a [JVMFileVectorStorage] for managing the storage and retrieval of these embeddings along with their associated documents.
 *
 * The primary responsibility of this class is to facilitate:
 * - Storing and embedding documents housed in a file system using a specified [DocumentEmbedder].
 * - Ranking documents based on similarity to query embeddings.
 * - Managing file-based vector storage via [JVMFileVectorStorage].
 *
 * @constructor Creates an instance of [JVMFileDocumentEmbeddingStorage].
 * @param embedder The embedder responsible for generating vector representations of file-based documents.
 * @param root The root directory path used as the base for file-based vector storage.
 */
public class JVMFileDocumentEmbeddingStorage(
    embedder: DocumentEmbedder<Path>,
    root: Path
) : FileDocumentEmbeddingStorage<Path, Path>(
    embedder = embedder,
    documentProvider = JVMDocumentProvider,
    fs = JVMFileSystemProvider.ReadWrite,
    root = root
)

/**
 * A JVM-specific implementation of `TextFileDocumentEmbeddingStorage` tailored for text document
 * embedding and storage within a file system. This class utilizes a `JVMDocumentProvider` to handle
 * document reading and manages embeddings using a provided `Embedder`.
 *
 * This storage solution is designed for efficient document querying and retrieval
 * based on embeddings derived from textual content.
 *
 * @constructor Creates an instance of `JVMTextFileDocumentEmbeddingStorage`.
 * @param embedder The embedding implementation used to generate and compare vector embeddings.
 * @param root The root directory where the document storage system is initialized.
 */
public class JVMTextFileDocumentEmbeddingStorage(
    embedder: Embedder,
    root: Path
) : TextFileDocumentEmbeddingStorage<Path, Path>(
    embedder = TextDocumentEmbedder(JVMDocumentProvider, embedder),
    documentProvider = JVMDocumentProvider,
    fs = JVMFileSystemProvider.ReadWrite,
    root = root
)
