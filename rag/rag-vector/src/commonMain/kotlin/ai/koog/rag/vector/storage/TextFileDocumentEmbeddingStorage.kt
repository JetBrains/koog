package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.vector.embedder.TextDocumentEmbedder

/**
 * A file-based [VectorStorage] implementation for text documents.
 *
 * This class specializes in storing and ranking text documents in a file system using embeddings derived from their
 * textual content.
 *
 * @param Document The type of document to be stored and processed.
 * @param embedder Converts text into vector embeddings and calculates similarity between embeddings.
 * @param documentProvider Provider for reading/writing documents.
 * @param fs Platform-specific file system provider for path manipulations.
 * @param root Root directory where all vector storage will be located.
 */
public open class TextFileDocumentEmbeddingStorage<Document, Path>(
    embedder: Embedder,
    documentProvider: DocumentProvider<Path, Document>,
    fs: FileSystemProvider.ReadWrite<Path>,
    root: Path
) : FileDocumentEmbeddingStorage<Document, Path>(
    embedder = TextDocumentEmbedder(documentProvider, embedder),
    documentProvider = documentProvider,
    fs = fs,
    root = root
)
