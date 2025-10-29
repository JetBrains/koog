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
import io.github.oshai.kotlinlogging.KotlinLogging

internal interface MemoryPostProcessor {
    suspend fun processFacts(facts: List<Fact>, options: MemoryRequestOptions): List<Fact>
}

@OptIn(InternalAgentsApi::class)
internal class SmartAgentMemoryProvider(
    private val delegate: AgentMemoryProvider,
    private val summaryProvider: SummaryProvider?,
    embedder: Embedder?,
    defaultBudget: TokenBudget?,
    tokenizer: Tokenizer = SimpleRegexBasedTokenizer()
) : AgentMemoryProvider, MemoryPostProcessor {

    private val logger = KotlinLogging.logger { }
    private val replayProcessor = FactReplayProcessor(embedder, tokenizer, defaultBudget)

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
        return replayProcessor.process(facts, options)
    }

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
}
