package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.bodyFieldsToCustomAttribute
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanType
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
internal class LangfuseSpanAdapter(
    private val traceAttributes: List<CustomAttribute>,
    private val openTelemetryConfig: OpenTelemetryConfig
) : SpanAdapter() {

    private val stepKey = AtomicInteger(0)

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INVOKE_AGENT -> {
                val runId =
                    span.attributes.find { attribute -> attribute.key == GenAIAttributes.Conversation.Id("").key }?.value

                runId?.let { runId ->
                    span.addAttribute(CustomAttribute("langfuse.session.id", runId))
                }

                traceAttributes.forEach { attribute ->
                    span.addAttribute(attribute)
                }
            }

            SpanType.INFERENCE -> {
                val eventsToProcess = span.events.toList()

                // Each event - convert into the span attribute
                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is SystemMessageEvent,
                        is UserMessageEvent,
                        is AssistantMessageEvent,
                        is ToolMessageEvent -> event.convertToPrompt(span, index)
                        is ChoiceEvent -> event.convertToPrompt(span, index)
                    }
                }
            }

            SpanType.NODE -> {
                val step = stepKey.getAndIncrement()

                span.addAttribute(
                    CustomAttribute(
                        "langfuse.observation.metadata.langgraph_step",
                        step
                    )
                )

                val nodeId =
                    span.attributes.find { attribute -> attribute.key == KoogAttributes.Koog.Node.Id("").key }?.value

                nodeId?.let { nodeId ->
                    span.addAttribute(
                        CustomAttribute(
                            "langfuse.observation.metadata.langgraph_node",
                            nodeId
                        )
                    )
                }
            }

            else -> {}
        }
    }

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INFERENCE -> {
                val eventsToProcess = span.events.toList()

                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is AssistantMessageEvent -> event.convertToCompletion(span, index)
                        is ChoiceEvent -> event.convertToCompletion(span, index)
                    }
                }
            }
            else -> {}
        }
    }

    //region Private Methods

    private fun GenAIAgentEvent.convertToPrompt(span: GenAIAgentSpan, index: Int) {
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            CustomAttribute("gen_ai.prompt.$index.${role.key}", role.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.prompt.$index.${content.key}", content.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    private fun ChoiceEvent.convertToPrompt(span: GenAIAgentSpan, index: Int) {
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            CustomAttribute("gen_ai.prompt.$index.${role.key}", role.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.ToolCalls>(this) { toolCalls ->
            CustomAttribute("gen_ai.prompt.$index.content", toolCalls.valueString(openTelemetryConfig.isVerbose))
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    private fun AssistantMessageEvent.convertToCompletion(span: GenAIAgentSpan, index: Int) {
        // Convert event data fields into the span attributes
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            CustomAttribute("gen_ai.completion.$index.${role.key}", role.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.completion.$index.${content.key}", content.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.ToolCalls>(this) { toolCalls ->
            CustomAttribute("gen_ai.completion.$index.content", toolCalls.valueString(openTelemetryConfig.isVerbose))
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(this) { finishReason ->
            CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    private fun ChoiceEvent.convertToCompletion(span: GenAIAgentSpan, index: Int) {
        // Convert event data fields into the span attributes
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            // Langfuse expects to have an assistant message for correctly displaying the responses from LLM.
            // Set a role explicitly to Assistant (even for LLM Tool Calls response).
            CustomAttribute("gen_ai.completion.$index.${role.key}", Message.Role.Assistant.name.lowercase())
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.completion.$index.${content.key}", content.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.ToolCalls>(this) { toolCalls ->
            CustomAttribute("gen_ai.completion.$index.content", toolCalls.valueString(openTelemetryConfig.isVerbose))
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(this) { finishReason ->
            CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    //endregion Private Methods
}
