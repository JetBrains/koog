package ai.koog.rag.base

/**
 * Represents a document accompanied by its associated payload.
 *
 * This data class ties a document of type [Document] to its corresponding payload of type [Payload].
 * It is commonly used in contexts where both the document and its additional metadata or associated data
 * (the payload) need to be stored, retrieved, or manipulated together.
 *
 * @param Document The type of the document being stored or processed.
 * @param Payload The type of the payload associated with the document.
 * @property document The document instance.
 * @property payload The payload associated with the document.
 */
public data class DocumentWithPayload<Document, Payload>(val document: Document, val payload: Payload)
