package ai.koog.rag.base.storage

public interface DeletionStorage<ID> {
    public suspend fun delete(ids: List<ID>, namespace: String? = null): List<ID>
}
