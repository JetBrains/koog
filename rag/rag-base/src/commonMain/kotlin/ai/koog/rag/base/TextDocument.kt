package ai.koog.rag.base

/**
 * A document with textual content, an optional identifier, and optional metadata.
 *
 * This interface is the common abstraction for any text-bearing document that can be stored in
 * or retrieved from a [ai.koog.rag.base.storage.SearchStorage] or
 * [ai.koog.rag.base.storage.WriteStorage].
 */
public interface TextDocument {
    /** The main textual content of the document. */
    public val content: String

    /** Optional unique identifier for the document. */
    public val id: String?

    /** Optional key-value metadata associated with the document. */
    public val metadata: Map<String, Any>
}
