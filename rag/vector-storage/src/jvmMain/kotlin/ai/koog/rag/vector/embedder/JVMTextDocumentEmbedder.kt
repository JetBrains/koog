package ai.koog.rag.vector.embedder

import ai.koog.embeddings.base.Embedder
import ai.koog.rag.base.files.JVMDocumentProvider
import java.nio.file.Path

/**
 * A specialization of [TextDocumentEmbedder] designed for embedding and processing text documents
 * in JVM-based file systems. It leverages a [ai.koog.rag.base.files.JVMDocumentProvider] to read content from `Path`
 * objects and uses an `Embedder` for generating and comparing vector embeddings of the text content.
 *
 * This class enables the transformation of text documents into vector representations and provides
 * utilities for measuring the similarity or difference between the embeddings of different documents.
 *
 * @constructor Creates a [JVMTextDocumentEmbedder] with the specified [embedder].
 * @param embedder The [ai.koog.embeddings.base.Embedder] used for generating vector embeddings and comparing embeddings.
 */
public class JVMTextDocumentEmbedder(embedder: Embedder) : TextDocumentEmbedder<Path, Path>(
    documentReader = JVMDocumentProvider,
    embedder = embedder
)
