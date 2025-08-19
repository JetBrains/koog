package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.bodyFieldsToCustomAttribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan
import ai.koog.prompt.message.Message

/**
 * WeaveSpanAdapter is a specialized implementation of [SpanAdapter] designed to process and transform
 * spans related to Generative AI agent events. It provides customized handling for specific types of
 * events, converting their data fields into span attributes and removing processed events.
 *
 * This adapter specifically handles spans of type [InferenceSpan] and processes events such as
 * [SystemMessageEvent], [UserMessageEvent], [AssistantMessageEvent], and [ToolMessageEvent].
 * Events are converted into custom span attributes for better traceability and observability.
 *
 * @param verbose A flag to control the verbosity of the processing.
 *        When set to true, additional details may be added to span attributes during processing.
 */
internal class WeaveSpanAdapter(private val verbose: Boolean) : SpanAdapter() {

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> {
                val eventsToProcess = span.events.toList()

                // Each event - convert into the span attribute
                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is SystemMessageEvent,
                        is UserMessageEvent,
                        is AssistantMessageEvent,
                        is ToolMessageEvent -> {
                            // Convert event data fields into the span attributes
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { role ->
                                CustomAttribute("gen_ai.prompt.$index.${role.key}", role.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { content ->
                                CustomAttribute("gen_ai.prompt.$index.${content.key}", content.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }
                    }
                }
            }
        }
    }

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span) {
            is InferenceSpan -> {
                val eventsToProcess = span.events.toList()

                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is AssistantMessageEvent -> {
                            // Convert event data fields into the span attributes
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { role ->
                                CustomAttribute("gen_ai.completion.$index.${role.key}", role.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { content ->
                                CustomAttribute("gen_ai.completion.$index.${content.key}", content.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.ToolCalls>(event) { toolCalls ->
                                CustomAttribute("gen_ai.completion.$index.content", toolCalls.valueString(verbose))
                            }

                            // Finish Reason
                            span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(event) { finishReason ->
                                CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }

                        is ChoiceEvent -> {
                            // Convert event data fields into the span attributes
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { role ->
                                // Weave expects to have an assistant message for correct displaying the responses from LLM.
                                // Set a role explicitly to Assistant (even for LLM Tool Calls response).
                                CustomAttribute("gen_ai.completion.$index.${role.key}", Message.Role.Assistant.name.lowercase())
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(event) { content ->
                                CustomAttribute("gen_ai.completion.$index.${content.key}", content.value)
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.ToolCalls>(event) { toolCalls ->
                                CustomAttribute("gen_ai.completion.$index.content", toolCalls.valueString(verbose))
                            }

                            span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(event) { finishReason ->
                                CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }
                    }
                }
            }
        }
    }
}
