package ai.koog.agents.features.opentelemetry.integration.langfuse

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
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import ai.koog.prompt.message.Message
import java.util.concurrent.atomic.AtomicInteger

/**
 * Internal adapter class for enhancing and modifying spans related to GenAI agent processing.
 * This class processes specific types of GenAIAgentSpan instances, particularly those
 * of type `InferenceSpan`, to transform and manage associated events and attributes
 * both before the span starts and before it finishes.
 *
 * The class operates on specific event types contained within the span and converts
 * their data into custom attributes. Additionally, it ensures that the converted events
 * are removed from the span's event list after conversion.
 */
internal class LangfuseSpanAdapter(private val verbose: Boolean) : SpanAdapter() {

    private val stepKey = AtomicInteger(0)

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

            is NodeExecuteSpan -> {
                val step = stepKey.getAndIncrement()

                span.addAttribute(
                    CustomAttribute(
                        "langfuse.observation.metadata.langgraph_step",
                        step
                    )
                )

                span.addAttribute(
                    CustomAttribute(
                        "langfuse.observation.metadata.langgraph_node",
                        span.nodeName
                    )
                )
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

                            span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(event) { finishReason ->
                                CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
                            }

                            // Delete event from the span
                            span.removeEvent(event)
                        }

                        is ChoiceEvent -> {
                            // Convert event data fields into the span attributes
                            span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(event) { role ->
                                // Langfuse expects to have an assistant message for correct displaying the responses from LLM.
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
