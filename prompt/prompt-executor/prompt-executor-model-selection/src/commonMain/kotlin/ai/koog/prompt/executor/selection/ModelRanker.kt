package ai.koog.prompt.executor.selection

import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Base API for [ModelRanker].
 *
 * A ranker orders accepted models into priority buckets. Models within one bucket have equal priority.
 */
public fun interface ModelRankerAPI {
    /**
     * Ranks [models] into ordered buckets from best to worst.
     */
    public suspend fun rank(models: List<LLModel>): Ranking
}

/**
 * Base class for custom model rankers.
 */
public abstract class ModelRanker : ModelRankerAPI

/**
 * A single ranking bucket containing models with equal priority.
 */
public data class RankBucket(public val models: List<LLModel>) {
    /**
     * Convenience constructor for creating a bucket from vararg [models].
     */
    public constructor(vararg models: LLModel) : this(models.toList())

    /**
     * Number of models in this bucket.
     */
    public val size: Int = models.size

    /**
     * True when bucket contains more than one model.
     */
    public fun hasTie(): Boolean = size > 1
}

/**
 * Ordered ranking represented as list of non-empty buckets.
 */
public data class Ranking(public val buckets: List<RankBucket>) {
    init {
        require(buckets.all { it.size > 0 }) {
            "All buckets must have at least one model."
        }
    }

    /**
     * Convenience constructor for creating ranking from vararg [buckets].
     */
    public constructor(vararg buckets: RankBucket) : this(buckets.toList())

    /**
     * Number of buckets in this ranking.
     */
    public val size: Int = buckets.size

    /**
     * True when at least one bucket has a tie.
     */
    public fun hasTies(): Boolean = buckets.any { it.hasTie() }
}

/**
 * Creates a [ModelRanker] from a suspend lambda.
 */
public fun ModelRanker(ranker: suspend (List<LLModel>) -> Ranking): ModelRanker =
    object : ModelRanker() {
        override suspend fun rank(models: List<LLModel>): Ranking = ranker(models)
    }

/**
 * Built-in [ModelRanker] factory functions.
 */
public object ModelRankers {
    /**
     * Prefers models with larger context window.
     */
    @JvmStatic
    public fun biggestContextLength(): ModelRanker =
        byDescending(onMissing = KeyRanker.OnMissing.RANK_WORST) { it.contextLength }

    /**
     * Prefers models with larger maximum output token count.
     */
    @JvmStatic
    public fun mostOutputTokens(): ModelRanker =
        byDescending(onMissing = KeyRanker.OnMissing.RANK_WORST) { it.maxOutputTokens }

    /**
     * Ranks models by ascending key.
     */
    @JvmStatic
    @JvmOverloads
    public fun <T : Comparable<T>> byAscending(
        onMissing: KeyRanker.OnMissing = KeyRanker.OnMissing.RANK_WORST,
        keySelector: (LLModel) -> T?,
    ): ModelRanker = KeyRanker(KeyRanker.Order.ASC, onMissing, keySelector)

    /**
     * Ranks models by descending key.
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
        /** Rank models with smaller key values first. */
        ASC,

        /** Rank models with larger key values first. */
        DESC,
    }

    /**
     * Behavior when a model's key is null.
     */
    public enum class OnMissing {
        /** Treat models with a missing key as best. */
        RANK_BEST,

        /** Treat models with a missing key as worst. */
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
