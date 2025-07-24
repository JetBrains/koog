package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.reflect.ToolFromCallable
import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.llm.LLMProvider

internal class ToolMessageEvent(
    private val provider: LLMProvider,
    private val toolCallId: String?,
    private val toolResult: ToolResult,
    private val toolArgs: ToolArgs = ToolArgs.Empty(),
    override val verbose: Boolean = false
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("tool.message")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))
    }

    override val bodyFields: List<EventBodyField> = buildList {
        // Content
        if (verbose) {
            add(EventBodyFields.Content(content = toolResult.toStringDefault()))
            if (toolArgs is ToolFromCallable.VarArgs ) {
                add(EventBodyFields.ToolParams(varArgs = toolArgs))
            }
        }

        // Id
        toolCallId?.let { id ->
            add(EventBodyFields.Id(id = id))
        }

        // Role (conditional).
        // Do not add Role as a tool result guarantee the response has a Tool role.
    }
}
