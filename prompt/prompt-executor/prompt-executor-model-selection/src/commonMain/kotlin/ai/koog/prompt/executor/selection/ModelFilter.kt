package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.selection.ModelFilterAPI.Companion.decide
import ai.koog.prompt.executor.selection.ModelFilterAPI.Decision
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmStatic

/**
 * Base API for [ModelFilter].
 *
 * Filters are hard constraints: only models accepted by all filters proceed to ranking.
 */
public fun interface ModelFilterAPI {
    /**
     * Evaluates [model] against this filter.
     */
    public suspend fun evaluate(model: LLModel): Decision

    /**
     * Binary filter result.
     */
    public enum class Decision {
        /** Model passed the filter. */
        ACCEPTED,

        /** Model failed the filter. */
        REJECTED,
    }

    public companion object {
        /** Converts a boolean predicate result to a [Decision]. */
        public fun decide(value: Boolean): Decision = if (value) Decision.ACCEPTED else Decision.REJECTED
    }
}

/**
 * Base class for custom model filters.
 */
public abstract class ModelFilter : ModelFilterAPI

/**
 * Creates a [ModelFilter] from a suspend lambda.
 */
public fun ModelFilter(filter: suspend (LLModel) -> Decision): ModelFilter =
    object : ModelFilter() {
        override suspend fun evaluate(model: LLModel): Decision = filter(model)
    }

/**
 * Built-in [ModelFilter] factory functions.
 */
public object ModelFilters {
    /**
     * Accepts only [model].
     */
    @JvmStatic
    public fun specific(model: LLModel): ModelFilter = SpecificModelFilter(model)

    /**
     * Accepts models supporting all [capabilities].
     */
    @JvmStatic
    public fun withCapabilities(vararg capabilities: LLMCapability): ModelFilter =
        CapabilitiesFilter(capabilities.toList())

    /**
     * Accepts models with context window at least [minTokens].
     */
    @JvmStatic
    public fun withMinContextLength(minTokens: Long): ModelFilter =
        MinContextLengthFilter(minTokens)
}

/**
 * Filter that accepts only one specific model value.
 */
public class SpecificModelFilter(
    private val model: LLModel
) : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision = decide(this.model == model)
}

/**
 * Filter that accepts models supporting all required capabilities.
 */
public class CapabilitiesFilter(
    private val capabilities: List<LLMCapability>,
) : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision =
        decide(capabilities.all { model.supports(it) })
}

/**
 * Filter that accepts models with context length greater than or equal to [minTokens].
 */
public class MinContextLengthFilter(
    private val minTokens: Long,
) : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision =
        decide((model.contextLength ?: 0L) >= minTokens)
}
