package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.EventAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message

internal class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    override val verbose: Boolean = false,
) : GenAIAgentEvent {

    override val name: String = super.name.concatName("choice")

    override val attributes: List<Attribute> = buildList {
        add(CommonAttributes.System(provider))

        when (message) {
            is Message.Assistant -> {
                add(EventAttributes.Body.Message(
                    role = message.role.takeIf { role -> role != Message.Role.Assistant },
                    content = message.content
                ))

                message.finishReason?.let { reason ->
                    add(EventAttributes.Body.FinishReason(reason))
                }

                add(EventAttributes.Body.Index(0))

            }
            is Message.Tool.Call -> {
                add(EventAttributes.Body.ToolCalls(tools = listOf(message), verbose = verbose))
                add(EventAttributes.Body.Index(0))
            }
        }
    }
}
