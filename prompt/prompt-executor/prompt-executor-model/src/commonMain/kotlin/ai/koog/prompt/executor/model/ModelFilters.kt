package ai.koog.prompt.executor.model

import ai.koog.prompt.executor.model.ModelFilter.Decision
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMCapability

public object ModelFilters {

    public fun specific(model: LLModel): ModelFilter =
        SpecificModelFilter(model)

    public fun withCapabilities(vararg capabilities: LLMCapability): ModelFilter =
        CapabilitiesFilter(capabilities.toList())

    public fun withMinContextWindow(minTokens: Long): ModelFilter =
        MinContextWindowFilter(minTokens)
}

public class SpecificModelFilter(
    private val model: LLModel
) : ModelFilter {
    override suspend fun evaluate(model: LLModel): Decision =
        decide(this.model == model)
}

public class CapabilitiesFilter(
    private val capabilities: List<LLMCapability>,
) : ModelFilter {
    override suspend fun evaluate(model: LLModel): Decision =
        decide(capabilities.all { model.supports(it) })
}

public class MinContextWindowFilter(
    private val minTokens: Long,
) : ModelFilter {
    override suspend fun evaluate(model: LLModel): Decision =
        decide((model.contextLength ?: 0L) >= minTokens)
}

private fun decide(bool: Boolean): Decision =
    if (bool) Decision.ACCEPTED else Decision.REJECTED
