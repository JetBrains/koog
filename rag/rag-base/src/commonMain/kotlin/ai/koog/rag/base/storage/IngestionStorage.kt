package ai.koog.rag.base.storage

public interface IngestionStorage<Document, ID> {
    public suspend fun add(documents: List<Document>, namespace: String? = null): List<ID>

    public suspend fun update(documents: Map<ID, Document>, namespace: String? = null): List<ID>
}
