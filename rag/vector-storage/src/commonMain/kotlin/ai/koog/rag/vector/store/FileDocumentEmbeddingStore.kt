package ai.koog.rag.vector.store

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.vector.embedder.DocumentEmbedder
import ai.koog.rag.vector.storage.FileVectorStorage

/**
 * A file-based [VectorStore] implementation.
 *
 * This class facilitates the storage and retrieval of documents and their corresponding vector embeddings
 * in a file system. It utilizes a [FileVectorStorage] for managing the document embeddings and extends
 * [EmbeddingStore], inheriting capabilities such as ranking, storing, and deleting documents
 * based on their embeddings.
 *
 * @param Document The type of the documents being stored.
 * @param embedder A mechanism responsible for embedding the documents into vector representations.
 * @param documentProvider Provider for reading/writing documents.
 * @param fs Platform-specific file system provider for path manipulations.
 * @param root Root directory where all vector storage will be located.
 */
public open class FileDocumentEmbeddingStore<Document, Path>(
    embedder: DocumentEmbedder<Document>,
    documentProvider: DocumentProvider<Path, Document>,
    fs: FileSystemProvider.ReadWrite<Path>,
    root: Path
) : EmbeddingStore<Document>(
    embedder = embedder,
    storage = FileVectorStorage(documentProvider, fs, root)
)
