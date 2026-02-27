package ai.koog.rag.base.storage

public interface ReadStorage<Document, ID> {
    public suspend fun read(ids: List<ID>, namespace: String? = null): Map<ID, Document>
}
