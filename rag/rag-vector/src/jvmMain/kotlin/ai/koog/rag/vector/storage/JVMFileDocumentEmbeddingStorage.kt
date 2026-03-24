package ai.koog.rag.vector.storage

import ai.koog.rag.base.files.JVMDocumentProvider
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.rag.vector.embedder.DocumentEmbedder
import java.nio.file.Path

/**
 * A file-system-based [VectorStorage] implementation for managing and embedding documents represented by file paths.
 *
 * This class extends [EmbeddingStorage] and is specialized for JVM-based systems where documents
 * are represented as file paths ([java.nio.file.Path]). It combines a [DocumentEmbedder] for embedding the file content into vectors
 * and a [ai.koog.rag.vector.backend.JVMFileVectorStorageBackend] for managing the storage and retrieval of these embeddings along with their associated documents.
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
