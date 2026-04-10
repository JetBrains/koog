package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject

/**
 * Event emitted for a single choice in an LLM completion.
 *
 * @param provider LLM provider that produced the choice.
 * @param message Response message carried by this choice.
 * @param arguments Optional structured arguments captured alongside the message.
 * @param index Position of this choice in a multi-choice response, or `null` for single-choice.
 */
public class ChoiceEvent(
    provider: LLMProvider,
    private val message: Message.Response,
    private val arguments: JsonObject? = null,
    index: Int? = null,
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

            is Message.Reasoning -> {
                addBodyField(EventBodyFields.Message(message.role, message.content))
            }

            is Message.Tool.Call -> {
                addBodyField(EventBodyFields.Role(role = message.role))
                addBodyField(EventBodyFields.ToolCalls(tools = listOf(message)))
                addBodyField(EventBodyFields.FinishReason(GenAIAttributes.Response.FinishReasonType.ToolCalls.id))
            }
        }
    }

    override val name: String = super.name.concatName("choice")
}
