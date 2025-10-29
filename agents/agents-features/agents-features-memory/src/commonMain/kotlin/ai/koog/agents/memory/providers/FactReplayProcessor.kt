package ai.koog.agents.memory.providers

import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.model.TokenBudget
import ai.koog.embeddings.base.Embedder
import ai.koog.prompt.tokenizer.Tokenizer
import ai.koog.rag.base.RankedDocument
import ai.koog.rag.base.RankedDocumentStorage
import ai.koog.rag.base.mostRelevantDocuments
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map

/**
 * Applies optional similarity ranking and token budgeting to the facts that will be replayed.
 *
 * Ranking is delegated to a [RankedDocumentStorage] supplied by [storageFactory], allowing callers
 * to swap in persistent/vector-backed implementations while the default uses in-memory scoring.
 */
internal class FactReplayProcessor(
    private val embedder: Embedder?,
    private val tokenizer: Tokenizer,
    private val defaultBudget: TokenBudget?,
    private val storageFactory: (List<Fact>, Embedder) -> RankedDocumentStorage<Fact> = { facts, activeEmbedder ->
        EphemeralRankedFactStorage(facts, activeEmbedder)
    }
) {

    private val logger = KotlinLogging.logger { }

    suspend fun process(facts: List<Fact>, options: MemoryRequestOptions): List<Fact> {
        if (facts.isEmpty()) return facts

        val ranked = rankFactsIfNeeded(facts, options.query)
        val effectiveBudget = options.budget ?: defaultBudget
        return applyTokenBudget(ranked, effectiveBudget)
    }

    private suspend fun rankFactsIfNeeded(facts: List<Fact>, query: String?): List<Fact> {
        val activeEmbedder = embedder ?: return facts
        val sanitizedQuery = query?.takeIf { it.isNotBlank() } ?: return facts

        return try {
            storageFactory(facts, activeEmbedder)
                .mostRelevantDocuments(query = sanitizedQuery, count = facts.size)
                .toList()
        } catch (throwable: Throwable) {
            logger.warn(throwable) { "Failed to rank facts by similarity – returning original order" }
            facts
        }
    }

    private fun applyTokenBudget(facts: List<Fact>, budget: TokenBudget?): List<Fact> {
        val effectiveBudget = budget ?: return facts
        if (effectiveBudget.maxFacts <= 0 || effectiveBudget.maxTokens <= 0) return emptyList()

        var tokens = 0
        val result = mutableListOf<Fact>()
        for (fact in facts) {
            if (result.size >= effectiveBudget.maxFacts) break
            val estimate = estimateTokensForFact(fact)
            if (tokens + estimate > effectiveBudget.maxTokens) continue
            result.add(fact)
            tokens += estimate
        }

        if (result.isEmpty() && facts.isNotEmpty()) {
            result.add(facts.first())
        }

        return result
    }

    private fun estimateTokensForFact(fact: Fact): Int = when (fact) {
        is SingleFact -> estimateTokens(fact.summary ?: fact.value)
        is MultipleFacts -> {
            val summary = fact.summary
            if (summary != null) {
                estimateTokens(summary)
            } else {
                fact.values.sumOf { estimateTokens(it) } + 1
            }
        }
    }

    private fun estimateTokens(text: String): Int = tokenizer.countTokens(text).coerceAtLeast(1)

    private fun Fact.displayText(): String = when (this) {
        is SingleFact -> summary ?: value
        is MultipleFacts -> summary ?: values.joinToString(separator = " ")
    }
}

private class EphemeralRankedFactStorage(
    private val facts: List<Fact>,
    private val embedder: Embedder
) : RankedDocumentStorage<Fact> {
    override suspend fun store(document: Fact, data: Unit): String =
        throw UnsupportedOperationException("Ephemeral ranked storage does not persist data")

    override suspend fun delete(documentId: String): Boolean =
        throw UnsupportedOperationException("Ephemeral ranked storage does not persist data")

    override suspend fun read(documentId: String): Fact? =
        throw UnsupportedOperationException("Ephemeral ranked storage does not persist data")

    override suspend fun getPayload(documentId: String): Unit = Unit

    override fun allDocuments(): Flow<Fact> = facts.asFlow()

    override fun rankDocuments(query: String): Flow<RankedDocument<Fact>> = facts.asFlow().map { fact ->
        val similarity = computeSimilarity(query, fact)
        RankedDocument(fact, similarity)
    }

    private suspend fun computeSimilarity(query: String, fact: Fact): Double {
        val queryEmbedding = embedder.embed(query)
        val factEmbedding = embedder.embed(fact.displayText())
        val distance = embedder.diff(queryEmbedding, factEmbedding)
        return 1.0 / (1.0 + distance)
    }

    private fun Fact.displayText(): String = when (this) {
        is SingleFact -> summary ?: value
        is MultipleFacts -> summary ?: values.joinToString(separator = " ")
    }
}
