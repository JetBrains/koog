package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

internal class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    private val arguments: JsonObject? = null,
    val index: Int? = null,
) : GenAIAgentEvent() {

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        index?.let { index -> addBodyField(EventBodyFields.Index(index)) }

        when (message) {
            is Message.Assistant -> {
                message.finishReason?.let { reason ->
                    addBodyField(EventBodyFields.FinishReason(reason))
                }

                addBodyField(
                    EventBodyFields.Message(
                        role = message.role,
                        content = message.content
                    )
                )

                arguments?.let { addBodyField(EventBodyFields.Arguments(it)) }
            }

            is Message.Tool.Call -> {
                addBodyField(EventBodyFields.Role(role = message.role))
                addBodyField(EventBodyFields.ToolCalls(tools = listOf(message)))
                addBodyField(EventBodyFields.FinishReason(SpanAttributes.Response.FinishReasonType.ToolCalls.id))
            }
        }
    }

    override val name: String = super.name.concatName("choice")
}
