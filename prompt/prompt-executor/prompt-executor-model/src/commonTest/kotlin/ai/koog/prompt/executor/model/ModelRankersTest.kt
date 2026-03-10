package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.KeyRanker.OnMissing
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelRankersTest {
    @Test
    fun testBiggestContextWindow() = runTest {
        // Given
        val small = model(id = "small", contextLength = 4_096)
        val large = model(id = "large", contextLength = 16_384)
        val missing = model(id = "missing", contextLength = null)

        // When
        val ranking = ModelRankers.biggestContextLength().rank(listOf(missing, small, large))

        // Then
        assertEquals(
            expected = Ranking(
                RankBucket(large),
                RankBucket(small),
                RankBucket(missing),
            ),
            actual = ranking
        )
    }

    @Test
    fun testMostOutputTokens() = runTest {
        // Given
        val low = model(id = "low", maxOutputTokens = 1_000)
        val medium = model(id = "medium", maxOutputTokens = 2_000)
        val high = model(id = "high", maxOutputTokens = 4_000)

        // When
        val ranking = ModelRankers.mostOutputTokens().rank(listOf(medium, high, low))

        // Then
        assertEquals(
            expected = Ranking(
                RankBucket(high),
                RankBucket(medium),
                RankBucket(low),
            ),
            actual = ranking
        )
    }

    @Test
    fun testByAscendingCreatesTieBuckets() = runTest {
        // Given
        val a = model(id = "a", maxOutputTokens = 2_000)
        val b = model(id = "b", maxOutputTokens = 2_000)
        val c = model(id = "c", maxOutputTokens = 4_000)

        // When
        val ranking = ModelRankers.byAscending { it.maxOutputTokens }.rank(listOf(a, b, c))

        // Then
        assertEquals(
            expected = Ranking(
                RankBucket(a, b),
                RankBucket(c),
            ),
            actual = ranking
        )
    }

    @Test
    fun testByDescendingRanksMissingAsBest() = runTest {
        // Given
        val value = model(id = "value", contextLength = 8_192)
        val missing = model(id = "missing", contextLength = null)

        // When
        val ranking = ModelRankers
            .byDescending(onMissing = OnMissing.RANK_BEST) { it.contextLength }
            .rank(listOf(value, missing))

        // Then
        assertEquals(
            expected = Ranking(
                RankBucket(missing),
                RankBucket(value),
            ),
            actual = ranking
        )
    }

    private fun model(
        id: String,
        contextLength: Long? = null,
        maxOutputTokens: Long? = null,
    ): LLModel = LLModel(
        provider = LLMProvider.OpenAI,
        id = id,
        contextLength = contextLength,
        maxOutputTokens = maxOutputTokens,
    )
}
