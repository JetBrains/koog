package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.KeyRanker.OnMissing
import ai.koog.prompt.executor.model.KeyRanker.Order
import ai.koog.prompt.llm.LLModel

/**
 * Factory helpers for common soft model rankers.
 */
public object ModelRankers {
    /**
     * Prefers models with larger context window.
     *
     * @return Ranker ordering by descending context window.
     */
    public fun biggestContextWindow(): ModelRanker =
        byDescending(onMissing = OnMissing.RANK_WORST) { it.contextLength }

    /**
     * Prefers models with larger maximum output token count.
     *
     * @return Ranker ordering by descending max output tokens.
     */
    public fun mostOutputTokens(): ModelRanker =
        byDescending(onMissing = OnMissing.RANK_WORST) { it.maxOutputTokens }

    /**
     * Ranks models by ascending key.
     *
     * @param onMissing Strategy for `null` keys.
     * @param keySelector Key extraction function.
     * @return Key-based ascending ranker.
     */
    public fun <T : Comparable<T>> byAscending(
        onMissing: OnMissing = OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(Order.ASC, onMissing, keySelector)

    /**
     * Ranks models by descending key.
     *
     * @param onMissing Strategy for `null` keys.
     * @param keySelector Key extraction function.
     * @return Key-based descending ranker.
     */
    public fun <T : Comparable<T>> byDescending(
        onMissing: OnMissing = OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(Order.DESC, onMissing, keySelector)
}

/**
 * Generic ranker grouping models by key and ordering buckets by key value.
 *
 * @constructor Creates key-based ranker.
 * @property order Key sort direction.
 * @property onMissing Missing-key strategy.
 * @property keySelector Key extraction function.
 */
public class KeyRanker<T : Comparable<T>>(
    private val order: Order,
    private val onMissing: OnMissing = OnMissing.RANK_WORST,
    private val keySelector: (LLModel) -> T?,
) : ModelRanker {

    /**
     * Key ordering direction.
     */
    public enum class Order {
        ASC,
        DESC,
    }

    /**
     * Behavior when key is missing (`null`).
     */
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
