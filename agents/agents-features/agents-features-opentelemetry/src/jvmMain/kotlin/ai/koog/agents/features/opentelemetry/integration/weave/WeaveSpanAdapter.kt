package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
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
import ai.koog.agents.features.opentelemetry.integration.isSdkArrayPrimitive
import ai.koog.agents.features.opentelemetry.integration.replaceAttributes
import ai.koog.agents.features.opentelemetry.integration.replaceBodyFields
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanType
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.Json

/**
 * WeaveSpanAdapter is a specialized implementation of [SpanAdapter] designed to process and transform
 * spans related to Generative AI agent events. It provides customized handling for specific types of
 * events, converting their data fields into span attributes and removing processed events.
 *
 * This adapter specifically handles spans of type [SpanType.INFERENCE] and processes events such as
 * [SystemMessageEvent], [UserMessageEvent], [AssistantMessageEvent], and [ToolMessageEvent].
 * Events are converted into custom span attributes for better traceability and observability.
 *
 * @param openTelemetryConfig A flag to control the verbosity of the processing.
 *        When set to true, additional details may be added to span attributes during processing.
 */
internal class WeaveSpanAdapter(private val openTelemetryConfig: OpenTelemetryConfig) : SpanAdapter() {

    companion object {
        private val json = Json { allowStructuredMapKeys = true }
    }

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INFERENCE -> {
                val eventsToProcess = span.events.toList()

                // Each event - convert into the span attribute
                eventsToProcess.forEachIndexed { index, event ->
                    when (event) {
                        is ToolMessageEvent -> event.convertToPrompt(span, index)

                        is SystemMessageEvent,
                        is UserMessageEvent,
                        is AssistantMessageEvent,
                        is ChoiceEvent -> event.convertToPrompt(span, index)
                    }
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
        // Convert event data fields into the span attributes
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            val roleValue =
                if (this is ChoiceEvent) {
                    Message.Role.Assistant.name.lowercase()
                } else {
                    role.value
                }

            CustomAttribute("gen_ai.prompt.$index.${role.key}", roleValue)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.prompt.$index.${content.key}", content.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Id>(this) { id ->
            CustomAttribute("gen_ai.prompt.$index.${id.key}", id.value)
        }

        span.replaceBodyFields<EventBodyFields.ToolCalls>(this) { toolCallsEvent ->
            val attributes = toolCallsEvent.value.flatMapIndexed { toolIndex, toolCallMap ->
                toolCallMap.map { (toolCallKey, toolCallValue) ->
                    val toolCallMapValue =
                        if (toolCallValue.isSdkArrayPrimitive) {
                            toolCallValue
                        } else {
                            json.encodeToString(toolCallsEvent.convertValueToJsonElement(toolCallValue, openTelemetryConfig.isVerbose))
                        }

                    CustomAttribute("gen_ai.prompt.$index.tool_calls.$toolIndex.$toolCallKey", toolCallMapValue)
                }
            }

            addAttributes(attributes)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(this) { finishReason ->
            CustomAttribute("gen_ai.prompt.$index.finish_reason", finishReason.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    private fun ToolMessageEvent.convertToPrompt(span: GenAIAgentSpan, index: Int) {
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            CustomAttribute("gen_ai.prompt.$index.${role.key}", role.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.prompt.$index.${content.key}", content.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Id>(this) { id ->
            CustomAttribute("gen_ai.prompt.$index.tool_call_id", id.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    private fun AssistantMessageEvent.convertToCompletion(span: GenAIAgentSpan, index: Int) {
        // Convert token attributes to Weave format
        span.replaceAttributes<GenAIAttributes.Usage.InputTokens> { attribute ->
            CustomAttribute("gen_ai.usage.prompt_tokens", attribute.value)
        }

        span.replaceAttributes<GenAIAttributes.Usage.OutputTokens> { attribute ->
            CustomAttribute("gen_ai.usage.completion_tokens", attribute.value)
        }

        // Convert event data fields into the span attributes
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            CustomAttribute("gen_ai.completion.$index.${role.key}", role.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.completion.$index.${content.key}", content.value)
        }

        span.replaceBodyFields<EventBodyFields.ToolCalls>(this) { toolCallsEvent ->
            val attributes = toolCallsEvent.value.flatMapIndexed { toolIndex, toolCallMap ->
                toolCallMap.map { (toolCallKey, toolCallValue) ->
                    val toolCallMapValue =
                        if (toolCallValue.isSdkArrayPrimitive) {
                            toolCallValue
                        } else {
                            json.encodeToString(toolCallsEvent.convertValueToJsonElement(toolCallValue, openTelemetryConfig.isVerbose))
                        }

                    CustomAttribute("gen_ai.completion.$index.tool_calls.$toolIndex.$toolCallKey", toolCallMapValue)
                }
            }

            addAttributes(attributes)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Id>(this) { id ->
            CustomAttribute("gen_ai.completion.$index.${id.key}", id.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(this) { finishReason ->
            CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    private fun ChoiceEvent.convertToCompletion(span: GenAIAgentSpan, index: Int) {
        // Convert tokens attributes to Weave format
        span.replaceAttributes<GenAIAttributes.Usage.InputTokens> { attribute ->
            CustomAttribute("gen_ai.usage.prompt_tokens", attribute.value)
        }

        span.replaceAttributes<GenAIAttributes.Usage.OutputTokens> { attribute ->
            CustomAttribute("gen_ai.usage.completion_tokens", attribute.value)
        }

        // Convert event data fields into the span attributes
        span.bodyFieldsToCustomAttribute<EventBodyFields.Role>(this) { role ->
            // Weave expects to have an assistant message for correct displaying the responses from LLM.
            // Set a role explicitly to Assistant (even for LLM Tool Calls response).
            CustomAttribute("gen_ai.completion.$index.${role.key}", Message.Role.Assistant.name.lowercase())
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Content>(this) { content ->
            CustomAttribute("gen_ai.completion.$index.${content.key}", content.value)
        }

        span.replaceBodyFields<EventBodyFields.ToolCalls>(this) { toolCallsEvent ->
            val attributes = toolCallsEvent.value.flatMapIndexed { toolIndex, toolCallMap ->
                toolCallMap.map { (toolCallKey, toolCallValue) ->
                    val toolCallMapValue =
                        if (toolCallValue.isSdkArrayPrimitive) {
                            toolCallValue
                        } else {
                            json.encodeToString(toolCallsEvent.convertValueToJsonElement(toolCallValue, openTelemetryConfig.isVerbose))
                        }

                    CustomAttribute("gen_ai.completion.$index.tool_calls.$toolIndex.$toolCallKey", toolCallMapValue)
                }
            }

            addAttributes(attributes)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.Id>(this) { id ->
            CustomAttribute("gen_ai.completion.$index.${id.key}", id.value)
        }

        span.bodyFieldsToCustomAttribute<EventBodyFields.FinishReason>(this) { finishReason ->
            CustomAttribute("gen_ai.completion.$index.finish_reason", finishReason.value)
        }

        // Delete event from the span
        span.removeEvent(this)
    }

    //endregion Private Methods
}
