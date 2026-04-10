package ai.koog.agents.features.opentelemetry.event

import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json

/**
 * Event emitted for a content-moderation outcome.
 *
 * @param provider LLM provider that produced the moderation result.
 * @param moderationResult Moderation outcome serialized into the event body.
 */
public class ModerationResponseEvent(
    provider: LLMProvider,
    private val moderationResult: ModerationResult
) : GenAIAgentEvent() {

    private companion object {
        private val json = Json { allowStructuredMapKeys = true }
    }

    init {
        // Attributes
        addAttribute(CommonAttributes.System(provider))

        // Body Fields
        addBodyField(EventBodyFields.Role(role = Message.Role.Assistant))
        addBodyField(EventBodyFields.Content(content = json.encodeToString(ModerationResult.serializer(), moderationResult)))
    }

    override val name: String = "moderation.result"
}
