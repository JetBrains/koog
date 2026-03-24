package ai.koog.rag.vector.storage

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.vector.embedder.TextDocumentEmbedder

/**
 * Implementation of an in-memory storage solution for text document embeddings.
 *
 * This class leverages the functionality of an embedder and a document provider
 * to compute embeddings for text documents and store them in memory for efficient retrieval.
 *
 * @param Document The type representing the document being managed.
 * @param Path The type representing the path or identifier for locating documents.
 * @param embedder The embedder used for generating vectorized representations of text.
 * @param documentReader The document provider facilitating access to document contents.
 */
public class InMemoryTextDocumentEmbeddingStorage<Document, Path>(
    embedder: Embedder,
    documentReader: DocumentProvider<Path, Document>
) : InMemoryDocumentEmbeddingStorage<Document>(
    embedder = TextDocumentEmbedder(documentReader, embedder),
)
