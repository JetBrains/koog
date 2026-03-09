package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.ModelFilter.Decision
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel

/**
 * Factory helpers for common hard model filters.
 */
public object ModelFilters {

    /**
     * Accepts only [model].
     *
     * @param model Model that must be accepted.
     * @return Filter accepting only the given model.
     */
    public fun specific(model: LLModel): ModelFilter =
        SpecificModelFilter(model)

    /**
     * Accepts models supporting all [capabilities].
     *
     * @param capabilities Required model capabilities.
     * @return Filter accepting models that support all capabilities.
     */
    public fun withCapabilities(vararg capabilities: LLMCapability): ModelFilter =
        CapabilitiesFilter(capabilities.toList())

    /**
     * Accepts models with context window at least [minTokens].
     *
     * @param minTokens Minimum required context window size.
     * @return Filter accepting models with sufficient context length.
     */
    public fun withMinContextWindow(minTokens: Long): ModelFilter =
        MinContextWindowFilter(minTokens)
}

/**
 * Filter that accepts only one specific model value.
 *
 * @constructor Creates filter bound to [model].
 * @property model Allowed model compared with structural equality.
 */
public class SpecificModelFilter(
    private val model: LLModel
) : ModelFilter {
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
) : ModelFilter {
    override suspend fun evaluate(model: LLModel): Decision =
        decide(capabilities.all { model.supports(it) })
}

/**
 * Filter that accepts models with context length greater than or equal to [minTokens].
 *
 * @constructor Creates minimum-context filter.
 * @property minTokens Minimum required context length in tokens.
 */
public class MinContextWindowFilter(
    private val minTokens: Long,
) : ModelFilter {
    override suspend fun evaluate(model: LLModel): Decision =
        decide((model.contextLength ?: 0L) >= minTokens)
}

private fun decide(bool: Boolean): Decision =
    if (bool) Decision.ACCEPTED else Decision.REJECTED
