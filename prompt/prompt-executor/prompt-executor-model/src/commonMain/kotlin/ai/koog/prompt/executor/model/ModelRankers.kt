package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.KeyRanker.OnMissing
import ai.koog.prompt.executor.model.KeyRanker.Order
import ai.koog.prompt.llm.LLModel

public object ModelRankers {
    public fun biggestContextWindow(): ModelRanker =
        byDescending(onMissing = OnMissing.RANK_WORST) { it.contextLength }

    public fun mostOutputTokens(): ModelRanker =
        byDescending(onMissing = OnMissing.RANK_WORST) { it.maxOutputTokens }

    public fun <T : Comparable<T>> byAscending(
        onMissing: OnMissing = OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(Order.ASC, onMissing, keySelector)

    public fun <T : Comparable<T>> byDescending(
        onMissing: OnMissing = OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(Order.DESC, onMissing, keySelector)
}

public class KeyRanker<T : Comparable<T>>(
    private val order: Order,
    private val onMissing: OnMissing = OnMissing.RANK_WORST,
    private val keySelector: (LLModel) -> T?,
) : ModelRanker {

    public enum class Order {
        ASC,
        DESC,
    }

    public enum class OnMissing {
        RANK_BEST,
        RANK_WORST,
    }

    private val comparator: Comparator<T?> by lazy {
        val nonNullComparator: Comparator<T> = when (order) {
            Order.ASC -> naturalOrder()
            Order.DESC -> reverseOrder()
        }
        when (onMissing) {
            OnMissing.RANK_BEST -> nullsFirst(nonNullComparator)
            OnMissing.RANK_WORST -> nullsLast(nonNullComparator)
        }
    }

    override suspend fun rank(models: List<LLModel>): Ranking =
        Ranking(bucketByKey(models, keySelector))

    private fun bucketByKey(models: List<LLModel>, keySelector: (LLModel) -> T?): List<RankBucket> {
        val buckets = models.groupBy(keySelector)
        return buckets.keys.sortedWith(comparator).map { key ->
            RankBucket(buckets.getValue(key))
        }
    }
}
