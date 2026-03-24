package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams

/**
 * Resolves effective tools based on model capabilities and tool choice.
 *
 * - Model supports tools → pass through
 * - No tools requested → empty
 * - Model doesn't support tools + Required/Named → reject
 * - Model doesn't support tools + Auto/None/null → silently drop
 */
@InternalAgentToolsApi
public fun List<ToolDescriptor>.resolveEffectiveTools(
    model: LLModel,
    toolChoice: LLMParams.ToolChoice?
): List<ToolDescriptor> = when {
    model.supports(LLMCapability.Tools) -> this

    this.isEmpty() -> emptyList()

    else -> {
        require(toolChoice !is LLMParams.ToolChoice.Required && toolChoice !is LLMParams.ToolChoice.Named) {
            "Model ${model.id} does not support tools, but tool use is required by toolChoice=$toolChoice"
        }
        emptyList()
    }
}
