package ai.koog.rag.vector

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.files.DocumentProvider

/**
 * Represents an interface for embedding documents into vector representations.
 * Implementations of this interface should define how specific document types
 * are processed and converted into embeddings for downstream applications.
 *
 * @param Document The type of the document to be embedded.
 */
public interface DocumentEmbedder<Document> : Embedder {
    /**
     * Converts the provided document into its vector representation.
     *
     * @param document The document to be embedded into a vector representation.
     * @return A vector representing the embedded form of the provided document.
     */
    public suspend fun embed(document: Document): Vector
}

/**
 * A class that provides functionality for embedding text documents and comparing their embeddings.
 *
 * This class uses a `DocumentProvider` to extract textual content from a generic document type,
 * and then utilizes an `Embedder` to convert the text into vector representations (embeddings).
 * The embeddings can be used to analyze or compare the documents.
 *
 * @param Document The type representing the document to be processed.
 * @property reader Reads content from documents of type [Document].
 * @property embedder Generates vector embeddings for given text and computes differences between embeddings.
 */
public open class TextDocumentEmbedder<Document, Path>(
    private val documentReader: DocumentProvider<Path, Document>,
    private val embedder: Embedder
) : DocumentEmbedder<Document> {
    /**
     * Converts the given document into its vector representation.
     *
     * @param document The document to be embedded.
     * @return A vector representation of the document.
     */
    override suspend fun embed(document: Document): Vector = embedder.embed(documentReader.text(document).toString())

    /**
     * Embeds the given text into a vector representation.
     *
     * @param text The text to embed.
     * @return A vector representation of the provided text.
     */
    override suspend fun embed(text: String): Vector = embedder.embed(text)

    /**
     * Calculates the difference between two embeddings using the underlying embedder.
     * Lower values indicate more similarity between the embeddings.
     *
     * @param embedding1 The first embedding to compare.
     * @param embedding2 The second embedding to compare.
     * @return A double value representing the difference between the two embeddings.
     */
    override fun diff(
        embedding1: Vector,
        embedding2: Vector
    ): Double = embedder.diff(embedding1, embedding2)
}