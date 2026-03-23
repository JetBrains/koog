@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Base API for [ModelRanker].
 *
 * A ranker orders a set of accepted [LLModel]s into a [Ranking] of priority [RankBucket]s.
 * Models within the same bucket are considered equal priority; buckets are ordered from best
 * to worst. Rankers must return exactly the models they received — no additions, removals,
 * or duplicates are allowed.
 *
 * To implement a custom ranker, extend [ModelRanker] rather than implementing this interface directly.
 * Use the [ModelRanker] factory function for simple cases.
 */
public fun interface ModelRankerAPI {
    /**
     * Ranks [models] into ordered buckets from best to worst.
     *
     * @param models Input models to rank, must not contain duplicates.
     * @return Ranking buckets ordered from best to worst, contains the same elements as [models].
     */
    public suspend fun rank(models: List<LLModel>): Ranking
}

/**
 * Abstract base class for custom model rankers.
 *
 * Subclass this to implement a custom ranker. Use the [ModelRanker] factory function for
 * simple cases where a lambda is sufficient.
 */
public expect abstract class ModelRanker() : ModelRankerAPI

/**
 * A single ranking bucket containing models with equal priority.
 *
 * @constructor Creates rank bucket from [models].
 * @property models Models in this bucket.
 */
public data class RankBucket(val models: List<LLModel>) {
    /**
     * Convenience constructor for creating a bucket from vararg [models].
     *
     * @param models Models in this bucket.
     */
    public constructor(vararg models: LLModel) : this(models.toList())

    /**
     * Number of models in this bucket.
     */
    public val size: Int = models.size

    /**
     * `true` when bucket contains more than one model.
     */
    public fun hasTie(): Boolean = size > 1
}

/**
 * Ordered ranking represented as list of non-empty buckets.
 *
 * @constructor Creates ranking from [buckets]. Empty buckets are not allowed.
 * @property buckets Ordered non-empty ranking buckets.
 * @throws IllegalArgumentException If any bucket is empty.
 */
public data class Ranking(val buckets: List<RankBucket>) {

    init {
        require(buckets.all { it.size > 0 }) {
            "All buckets must have at least one model."
        }
    }

    /**
     * Convenience constructor for creating ranking from vararg [buckets].
     *
     * @param buckets Ordered non-empty ranking buckets.
     */
    public constructor(vararg buckets: RankBucket) : this(buckets.toList())

    /**
     * Number of buckets in this ranking.
     */
    public val size: Int = buckets.size

    /**
     * `true` when at least one bucket has a tie.
     */
    public fun hasTies(): Boolean = buckets.any { it.hasTie() }
}

/**
 * Creates a [ModelRanker] from a suspend lambda.
 *
 * @param ranker Suspend lambda that produces a [Ranking] from the input models.
 *   Must return exactly the models it receives — no additions, removals, or duplicates.
 */
public fun ModelRanker(ranker: suspend (List<LLModel>) -> Ranking): ModelRanker = object : ModelRanker() {
    override suspend fun rank(models: List<LLModel>): Ranking = ranker(models)
}

/**
 * Built-in [ModelRanker] factory functions.
 *
 * Each function returns a ready-to-use [ModelRanker] for common ranking scenarios.
 * Multiple rankers can be combined via [ModelSelectorBuilder] for lexicographic tie-breaking.
 */
public object ModelRankers {
    /**
     * Prefers models with larger context window.
     *
     * @return Ranker ordering by descending context window.
     */
    @JvmStatic
    public fun biggestContextLength(): ModelRanker =
        byDescending(onMissing = KeyRanker.OnMissing.RANK_WORST) { it.contextLength }

    /**
     * Prefers models with larger maximum output token count.
     *
     * @return Ranker ordering by descending max output tokens.
     */
    @JvmStatic
    public fun mostOutputTokens(): ModelRanker =
        byDescending(onMissing = KeyRanker.OnMissing.RANK_WORST) { it.maxOutputTokens }

    /**
     * Ranks models by ascending key.
     *
     * @param onMissing Strategy for `null` keys.
     * @param keySelector Key extraction function.
     * @return Key-based ascending ranker.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T : Comparable<T>> byAscending(
        onMissing: KeyRanker.OnMissing = KeyRanker.OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(KeyRanker.Order.ASC, onMissing, keySelector)

    /**
     * Ranks models by descending key.
     *
     * @param onMissing Strategy for `null` keys.
     * @param keySelector Key extraction function.
     * @return Key-based descending ranker.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T : Comparable<T>> byDescending(
        onMissing: KeyRanker.OnMissing = KeyRanker.OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(KeyRanker.Order.DESC, onMissing, keySelector)
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
) : ModelRanker() {

    /**
     * Key ordering direction.
     */
    public enum class Order {
        /** Rank models with smaller key values first (ascending). */
        ASC,

        /** Rank models with larger key values first (descending). */
        DESC,
    }

    /**
     * Behavior when a model's key is `null`.
     */
    public enum class OnMissing {
        /** Treat models with a missing key as if they had the best possible key value. */
        RANK_BEST,

        /** Treat models with a missing key as if they had the worst possible key value. */
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
