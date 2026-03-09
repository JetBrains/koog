package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.ModelFilter.Decision
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultModelSelectorTest {
    private val maxConcurrentlyFilteredModels = 2

    @Test
    fun testFiltersAndRanksModels() = runTest {
        // Given: models
        val gptMini = LLModel(provider = LLMProvider.OpenAI, id = "gpt-5-mini", maxOutputTokens = 1_000)
        val gpt = LLModel(provider = LLMProvider.OpenAI, id = "gpt-5", maxOutputTokens = 2_000)
        val gptCodex = LLModel(provider = LLMProvider.OpenAI, id = "gpt-5-codex", maxOutputTokens = 3_000)
        val gptPro = LLModel(provider = LLMProvider.OpenAI, id = "gpt-5-pro", maxOutputTokens = 3_000)
        val google = LLModel(provider = LLMProvider.Google, id = "gemini", maxOutputTokens = 2_000)

        // And: filters
        val onlyOpenAI = ModelFilter { model ->
            if (model.provider == LLMProvider.OpenAI) Decision.ACCEPTED else Decision.REJECTED
        }
        val atLeast2000Tokens = ModelFilter { model ->
            val has2000TokensOrMore = model.maxOutputTokens?.let { it >= 2_000 } ?: false
            if (has2000TokensOrMore) Decision.ACCEPTED else Decision.REJECTED
        }
        val filters = listOf(onlyOpenAI, atLeast2000Tokens)

        // And: rankers
        val preferCodex = ModelRanker { models ->
            val codexModels = models.filter { it.id.contains("codex") }
            val otherModels = models - codexModels
            Ranking(
                RankBucket(codexModels),
                RankBucket(otherModels)
            )
        }
        val rankers = listOf(ModelRankers.mostOutputTokens(), preferCodex)

        // When
        val selectedModels = selector(filters + rankers).select(
            models = listOf(gptMini, gpt, gptCodex, gptPro, google),
        )

        // Then
        assertEquals(listOf(gptCodex, gptPro, gpt), selectedModels.ranked)
    }

    @Test
    fun testReturnsOnlyAcceptedModelsWhenNoRankersProvided() = runTest {
        // Given
        val (modelA, modelB, modelC) = modelsWithIds("a", "b", "c")

        // And
        val rejectB = ModelFilter { model ->
            if (model.id == "b") Decision.REJECTED else Decision.ACCEPTED
        }

        // When
        val result = selector(listOf(rejectB)).select(
            models = listOf(modelA, modelB, modelC),
        )

        // Then
        assertEquals(listOf(modelA, modelC), result.ranked)
    }

    @Test
    fun testReturnsEmptyListWhenNoModelsPassFiltering() = runTest {
        // Given
        val rejectAll = ModelFilter { Decision.REJECTED }

        // When
        val result = selector(listOf(rejectAll)).select(
            models = models(count = 5),
        )

        // Then
        assertEquals(emptyList(), result.ranked)
    }

    @Test
    fun testAppliesSubsequentRankersToResolveTies() = runTest {
        // Given
        val modelA = LLModel(
            provider = LLMProvider.OpenAI,
            id = "a",
            maxOutputTokens = 1_000
        )
        val modelB = LLModel(
            provider = LLMProvider.OpenAI,
            id = "b",
            maxOutputTokens = 1_000
        )
        val modelC = LLModel(
            provider = LLMProvider.OpenAI,
            id = "c",
            maxOutputTokens = 2_000
        )

        // And
        val rankers = listOf(
            ModelRankers.mostOutputTokens(),
            ModelRankers.byAscending { it.id }
        )

        // When:
        val result = selector(rankers).select(
            models = listOf(modelA, modelB, modelC),
        )

        // Then:
        assertEquals(listOf(modelC, modelA, modelB), result.ranked)
    }

    @Test
    fun testFailsWhenRankerReturnsTooMany() = runTest {
        // Given:
        val inputModels = models(count = 3)

        // And:
        val excessiveRanker = ModelRanker {
            Ranking(
                RankBucket(inputModels[0]),
                RankBucket(inputModels[1], inputModels[2]),
                RankBucket(model("unexpected_bucket"))
            )
        }

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            selector(listOf(excessiveRanker)).select(
                models = inputModels,
            )
        }
    }

    @Test
    fun testFailsWhenRankerReturnsTooFewModels() = runTest {
        // Given
        val inputModels = models(count = 3)

        // And
        val modelDroppingRanker = ModelRanker {
            Ranking(
                RankBucket(inputModels[0], inputModels[1]),
            )
        }

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            selector(listOf(modelDroppingRanker)).select(
                models = inputModels,
            )
        }
    }

    @Test
    fun testFailsWhenInputContainsDuplicateModels() = runTest {
        // Given
        val first = model("dup")
        val second = first.copy()

        // And
        val inputModels = listOf(first, second, model("other"))

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            selector().select(
                models = inputModels,
            )
        }
    }

    @Test
    fun testFailsWhenRankerReturnsDuplicateModels() = runTest {
        // Given
        val inputModels = models(count = 3)

        // And
        val duplicateReturningRanker = ModelRanker { models ->
            Ranking(
                RankBucket(models[0], models[0]),
                RankBucket(models[1], models[2]),
            )
        }

        // When, Then
        assertFailsWith<IllegalArgumentException> {
            selector(listOf(duplicateReturningRanker)).select(
                models = inputModels,
            )
        }
    }

    private fun models(count: Int): List<LLModel> =
        (0 until count).map { model("model-$it") }

    private fun modelsWithIds(vararg ids: String): List<LLModel> =
        ids.map { model(it) }

    private fun model(id: String): LLModel =
        LLModel(
            provider = LLMProvider.OpenAI,
            id = id,
        )

    private fun selector(steps: List<ModelSelectionStep> = emptyList()): DefaultModelSelector =
        DefaultModelSelector(
            *steps.toTypedArray(),
            maxConcurrentlyFilteredModels = maxConcurrentlyFilteredModels
        )
}
