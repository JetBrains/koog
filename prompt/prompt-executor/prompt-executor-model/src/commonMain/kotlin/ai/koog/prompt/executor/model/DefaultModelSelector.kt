package ai.koog.prompt.executor.model

import ai.koog.prompt.llm.LLModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

public class DefaultModelSelector(
    private val maxConcurrentlyFilteredModels: Int = 8,
) : ModelSelector {
    init {
        require(maxConcurrentlyFilteredModels > 0) { "maxConcurrentFilteredModels must be greater than 0." }
    }

    override suspend fun select(models: List<LLModel>, steps: List<ModelSelectionStep>): ModelSelection {
        if (models.isEmpty()) return ModelSelection.EMPTY
        validateModelsInput(models)

        val filters = steps.filterIsInstance<ModelFilter>()
        val rankers = steps.filterIsInstance<ModelRanker>()

        val accepted = filterAccepted(models, filters)
        val ranked = rankLexicographically(accepted, rankers)

        return ModelSelection(ranked = ranked)
    }

    private suspend fun rankLexicographically(models: List<LLModel>, rankers: List<ModelRanker>): List<LLModel> {
        if (models.isEmpty() || rankers.isEmpty()) return models

        var ranking = rankers.first().rank(models)
        val inputSet = models.toSet()
        validateRanking(inputSet, ranking)
        for (i in 1 until rankers.size) {
            ranking = resolveTies(rankers[i], ranking)
        }
        return ranking.buckets.flatMap { it.models }
    }

    private suspend fun resolveTies(ranker: ModelRanker, ranking: Ranking): Ranking {
        if (!ranking.hasTies()) return ranking

        val resolvedBuckets = ArrayList<RankBucket>(ranking.size)
        for (bucket in ranking.buckets) {
            if (bucket.hasTie()) {
                val nestedRanking = ranker.rank(bucket.models)
                validateRanking(bucket.models.toSet(), nestedRanking)
                resolvedBuckets += nestedRanking.buckets
            } else {
                resolvedBuckets += bucket
            }
        }
        return Ranking(resolvedBuckets)
    }

    private fun validateModelsInput(models: List<LLModel>) {
        validateDuplicates(models) { duplicatedModels ->
            "Duplicate models found: ${duplicatedModels.joinToString { it.id }}"
        }
    }

    private fun validateDuplicates(subject: List<LLModel>, lazyMessage: (Set<LLModel>) -> String) {
        val seen = HashSet<LLModel>(subject.size)
        val duplicateModels = LinkedHashSet<LLModel>()
        for (model in subject) {
            if (!seen.add(model)) {
                duplicateModels += model
            }
        }
        require(duplicateModels.isEmpty()) { lazyMessage(duplicateModels) }
    }

    private fun validateRanking(inputSet: Set<LLModel>, ranking: Ranking) {
        val rankedModels = ranking.buckets.flatMap { it.models }
        validateDuplicates(rankedModels) { duplicatedModels ->
            "Duplicate models found in ranking: ${duplicatedModels.joinToString { it.id }}"
        }

        val rankedModelSet = rankedModels.toSet()
        if (rankedModelSet != inputSet) {
            val missingModels = inputSet.filter { it !in rankedModelSet }
            require(missingModels.isEmpty()) {
                "Ranker did not return models from the initial input: ${missingModels.joinToString { it.id }}"
            }
            val extraModels = rankedModelSet.filter { it !in inputSet }
            require(extraModels.isEmpty()) {
                "Ranker returned models not in the initial input: ${extraModels.joinToString { it.id }}"
            }
        }
    }

    private suspend fun filterAccepted(models: List<LLModel>, filters: List<ModelFilter>): List<LLModel> {
        if (filters.isEmpty()) return models
        val semaphore = Semaphore(maxConcurrentlyFilteredModels)
        return coroutineScope {
            models.map { model ->
                async {
                    semaphore.withPermit {
                        if (filters.all { filter -> filter.evaluate(model) == ModelFilter.Decision.ACCEPTED }) {
                            model
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
