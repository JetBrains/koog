package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.extension.eventBodyFieldToAttribute
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan

@OptIn(InternalAgentsApi::class)
internal object WeaveSpanAdapter : SpanAdapter() {

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> span.prepareSpanAttributes()
        }
    }

    //region Private Methods

    private fun InferenceSpan.prepareSpanAttributes() {
        this.events.forEach { event ->
            when (event) {
                is AssistantMessageEvent -> {
                    this.eventBodyFieldToAttribute<EventBodyFields.Role>(event) { role ->
                        CustomAttribute("gen_ai.completion.${role.key}", role.value)
                    }

                    this.eventBodyFieldToAttribute<EventBodyFields.Content>(event) { content ->
                        CustomAttribute("gen_ai.completion.content", content.value)
                    }
                }

                is ChoiceEvent -> {
                    val index = event.index

                    this.eventBodyFieldToAttribute<EventBodyFields.Role>(event) { role ->
                        CustomAttribute("gen_ai.$index.${role.key}", role.value)
                    }

                    this.eventBodyFieldToAttribute<EventBodyFields.Content>(event) { content ->
                        CustomAttribute("gen_ai.completion.$index.content", content.value)
                    }
                }

                is UserMessageEvent -> {
                    this.eventBodyFieldToAttribute<EventBodyFields.Content>(event) { content ->
                        CustomAttribute("gen_ai.completion.content", content.value)
                    }
                }

                else -> { }
            }
        }
    }

    //endregion Private Methods
}
