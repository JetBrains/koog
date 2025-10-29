package ai.koog.agents.memory.providers

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.memory.feature.summarization.SummaryProvider
import ai.koog.agents.memory.feature.summarization.SummaryResult
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.Fact
import ai.koog.agents.memory.model.MemoryScope
import ai.koog.agents.memory.model.MemorySubject
import ai.koog.agents.memory.model.MultipleFacts
import ai.koog.agents.memory.model.SingleFact
import ai.koog.agents.memory.model.TokenBudget
import ai.koog.embeddings.base.Embedder
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import ai.koog.prompt.tokenizer.Tokenizer
import ai.koog.rag.base.DocumentWithPayload
import ai.koog.rag.base.RankedDocument
import ai.koog.rag.base.RankedDocumentStorage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList

/**
 * Decorator that enriches, ranks, and budgets delegate memory operations without modifying the
 * base [AgentMemoryProvider] contract.
 */
internal interface MemoryPostProcessor {
    suspend fun processFacts(facts: List<Fact>, options: MemoryRequestOptions): List<Fact>
}

@OptIn(InternalAgentsApi::class)
internal class SmartAgentMemoryProvider(
    private val delegate: AgentMemoryProvider,
    private val summaryProvider: SummaryProvider?,
    private val embedder: Embedder?,
    private val defaultBudget: TokenBudget?,
    private val tokenizer: Tokenizer = SimpleRegexBasedTokenizer()
) : AgentMemoryProvider, MemoryPostProcessor {

    private val logger = KotlinLogging.logger { }

    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        delegate.save(fact, subject, scope)
    }

    override suspend fun save(
        fact: Fact,
        subject: MemorySubject,
        scope: MemoryScope,
        options: MemoryRequestOptions
    ) {
        val enriched = if (options.enrich) enrichFact(fact) else fact
        val downstreamOptions = if (options.enrich) options.copy(enrich = false) else options
        delegate.save(enriched, subject, scope, downstreamOptions)
    }

    override suspend fun load(
        concept: Concept,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> = delegate.load(concept, subject, scope)

    override suspend fun load(
        concept: Concept,
        subject: MemorySubject,
        scope: MemoryScope,
        options: MemoryRequestOptions
    ): List<Fact> = delegate.load(concept, subject, scope, options)

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> =
        delegate.loadAll(subject, scope)

    override suspend fun loadAll(
        subject: MemorySubject,
        scope: MemoryScope,
        options: MemoryRequestOptions
    ): List<Fact> = delegate.loadAll(subject, scope, options)

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> = delegate.loadByDescription(description, subject, scope)

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope,
        options: MemoryRequestOptions
    ): List<Fact> = delegate.loadByDescription(description, subject, scope, options)

    private suspend fun enrichFact(fact: Fact): Fact {
        val provider = summaryProvider ?: return fact
        return try {
            val result = provider.summarize(fact)
            fact.withSummary(result)
        } catch (throwable: Throwable) {
            logger.warn(throwable) { "Failed to enrich fact '${fact.concept.keyword}' – storing original fact" }
            fact
        }
    }

    override suspend fun processFacts(
        facts: List<Fact>,
        options: MemoryRequestOptions
    ): List<Fact> {
        if (facts.isEmpty()) return facts
        val ranked = rankFactsIfNeeded(facts, options.query)
        val effectiveBudget = options.budget ?: defaultBudget
        return applyTokenBudget(ranked, effectiveBudget)
    }

    private suspend fun rankFactsIfNeeded(facts: List<Fact>, query: String?): List<Fact> {
        val activeEmbedder = embedder ?: return facts
        val sanitizedQuery = query?.takeIf { it.isNotBlank() } ?: return facts

        return try {
            val storage = EphemeralRankedFactStorage(facts, activeEmbedder)
            storage.rankDocuments(sanitizedQuery)
                .toList()
                .sortedByDescending { it.similarity }
                .map { it.document }
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

    private fun Fact.withSummary(result: SummaryResult): Fact = when (this) {
        is SingleFact -> {
            val newSummary = result.summary ?: summary
            val newKeywords = if (result.keywords.isEmpty()) keywords else result.keywords
            if (newSummary == summary && newKeywords == keywords) {
                this
            } else {
                copy(summary = newSummary, keywords = newKeywords)
            }
        }

        is MultipleFacts -> {
            val newSummary = result.summary ?: summary
            val newKeywords = if (result.keywords.isEmpty()) keywords else result.keywords
            if (newSummary == summary && newKeywords == keywords) {
                this
            } else {
                copy(summary = newSummary, keywords = newKeywords)
            }
        }
    }

    private fun Fact.displayText(): String = when (this) {
        is SingleFact -> summary ?: value
        is MultipleFacts -> summary ?: values.joinToString(separator = " ")
    }
}

private class EphemeralRankedFactStorage(
    private val facts: List<Fact>,
    private val embedder: Embedder
) : RankedDocumentStorage<Fact> {
    override suspend fun store(document: Fact, data: Unit): String {
        throw UnsupportedOperationException("Ephemeral storage does not support persistence operations")
    }

    override suspend fun delete(documentId: String): Boolean {
        throw UnsupportedOperationException("Ephemeral storage does not support persistence operations")
    }

    override suspend fun read(documentId: String): Fact? {
        throw UnsupportedOperationException("Ephemeral storage does not support persistence operations")
    }

    override suspend fun getPayload(documentId: String): Unit = Unit

    override suspend fun readWithPayload(documentId: String): DocumentWithPayload<Fact, Unit>? = null

    override fun allDocuments(): Flow<Fact> = facts.asFlow()

    override fun allDocumentsWithPayload(): Flow<DocumentWithPayload<Fact, Unit>> =
        facts.map { DocumentWithPayload(it, Unit) }.asFlow()

    override fun rankDocuments(query: String): Flow<RankedDocument<Fact>> = flow {
        val queryEmbedding = embedder.embed(query)
        for (fact in facts) {
            val text = fact.displayText()
            val factEmbedding = embedder.embed(text)
            val distance = embedder.diff(queryEmbedding, factEmbedding)
            val similarity = 1.0 / (1.0 + distance)
            emit(RankedDocument(fact, similarity))
        }
    }

    private fun Fact.displayText(): String = when (this) {
        is SingleFact -> summary ?: value
        is MultipleFacts -> summary ?: values.joinToString(separator = " ")
    }
}
