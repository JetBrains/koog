package ai.koog.rag.base.storage

/**
 * Represents a document ranked by its similarity score.
 *
 * This data class encapsulates a document of generic type [Document] along with
 * a similarity score, which quantifies the relevance of the document according
 * to a given query or context. The higher the similarity score, the more relevant
 * the document is considered to be.
 *
 * @param Document The type of the document being ranked.
 * @property document The document instance being ranked.
 * @property similarity A double value representing the similarity or relevance score
 * of the document.
 */
public data class SearchResult<Document>(val document: Document, val similarity: Double)
