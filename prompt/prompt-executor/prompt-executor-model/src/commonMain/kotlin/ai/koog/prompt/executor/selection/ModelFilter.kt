@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.prompt.executor.selection

import ai.koog.prompt.executor.selection.ModelFilterAPI.Companion.decide
import ai.koog.prompt.executor.selection.ModelFilterAPI.Decision
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmStatic

/**
 * Base API for [ModelFilter].
 *
 * A filter evaluates a single [LLModel] and returns a binary [Decision]. Filters are applied
 * before ranking: only models accepted by **all** configured filters proceed to the ranking stage.
 *
 * To implement a custom filter, extend [ModelFilter] rather than implementing this interface directly.
 * Use the [ModelFilter] factory function for simple cases.
 */
public fun interface ModelFilterAPI {
    /**
     * Evaluates model against filter criteria.
     *
     * @param model Model to evaluate.
     * @return Binary filter decision.
     */
    public suspend fun evaluate(model: LLModel): Decision

    /**
     * Binary result of a filter evaluation.
     */
    public enum class Decision {
        /** Model passed the filter and may proceed to the next stage. */
        ACCEPTED,

        /** Model was rejected and will not be considered further. */
        REJECTED,
    }

    public companion object {
        /** Converts a boolean predicate result to the corresponding [Decision]. */
        public fun decide(boolean: Boolean): Decision = if (boolean) Decision.ACCEPTED else Decision.REJECTED
    }
}

/**
 * Abstract base class for custom model filters.
 *
 * Subclass this to implement a custom filter. Use the [ModelFilter] factory function for
 * simple cases where a lambda is sufficient.
 */
public expect abstract class ModelFilter() : ModelFilterAPI

/**
 * Creates a [ModelFilter] from a suspend lambda.
 *
 * @param filter Suspend lambda returning the filter [ModelFilterAPI.Decision].
 */
public fun ModelFilter(filter: suspend (LLModel) -> Decision): ModelFilter = object : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision = filter(model)
}

/**
 * Built-in [ModelFilter] factory functions.
 *
 * Each function returns a ready-to-use [ModelFilter] for common filtering scenarios.
 * Multiple filters can be combined via [ModelSelectorBuilder].
 */
public object ModelFilters {
    /**
     * Accepts only [model].
     *
     * @param model Model that must be accepted.
     * @return Filter accepting only the given model.
     */
    @JvmStatic
    public fun specific(model: LLModel): ModelFilter =
        SpecificModelFilter(model)

    /**
     * Accepts models supporting all [capabilities].
     *
     * @param capabilities Required model capabilities.
     * @return Filter accepting models that support all capabilities.
     */
    @JvmStatic
    public fun withCapabilities(vararg capabilities: LLMCapability): ModelFilter =
        CapabilitiesFilter(capabilities.toList())

    /**
     * Accepts models with context window at least [minTokens].
     *
     * @param minTokens Minimum required context window size.
     * @return Filter accepting models with sufficient context length.
     */
    @JvmStatic
    public fun withMinContextLength(minTokens: Long): ModelFilter =
        MinContextLengthFilter(minTokens)
}

/**
 * Filter that accepts only one specific model value.
 *
 * @constructor Creates filter bound to [model].
 * @property model Allowed model compared with structural equality.
 */
public class SpecificModelFilter(
    private val model: LLModel
) : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision =
        decide(this.model == model)
}

/**
 * Filter that accepts models supporting all required capabilities.
 *
 * @constructor Creates capability-based filter from [capabilities].
 * @property capabilities Required capabilities.
 */
public class CapabilitiesFilter(
    private val capabilities: List<LLMCapability>,
) : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision =
        decide(capabilities.all { model.supports(it) })
}

/**
 * Filter that accepts models with context length greater than or equal to [minTokens].
 *
 * @constructor Creates minimum-context filter.
 * @property minTokens Minimum required context length in tokens.
 */
public class MinContextLengthFilter(
    private val minTokens: Long,
) : ModelFilter() {
    override suspend fun evaluate(model: LLModel): Decision =
        decide((model.contextLength ?: 0L) >= minTokens)
}
