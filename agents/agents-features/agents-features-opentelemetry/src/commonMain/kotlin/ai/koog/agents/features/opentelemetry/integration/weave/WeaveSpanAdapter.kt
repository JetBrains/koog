package ai.koog.agents.features.opentelemetry.integration.weave

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.integration.replaceAttributes
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanType
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * WeaveSpanAdapter is a specialized implementation of [SpanAdapter] that reshapes inference-span
 * content from the OTel-standard `gen_ai.input.messages` / `gen_ai.output.messages` attributes
 * (which Weave does not currently recognize) into the indexed `gen_ai.prompt.{i}.*` /
 * `gen_ai.completion.{i}.*` attributes that Weave displays. It also renames token-usage
 * attributes to the legacy `prompt_tokens` / `completion_tokens` form Weave's UI surfaces.
 *
 * The adapter consumes the typed `messages: List<Message>` exposed by those standard attributes
 * and re-emits them in the indexed shape; the original attributes remain on the span untouched.
 * Sensitive payload values are wrapped in [HiddenString]; the standard `applyAttributes` pipeline
 * handles the verbose flag uniformly.
 */
internal class WeaveSpanAdapter(
    @Suppress("unused")
    private val openTelemetryConfig: OpenTelemetryConfig,
) : SpanAdapter() {

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INFERENCE -> {
                // Decompose `gen_ai.input.messages` into Weave-shaped indexed prompt attributes.
                // The OTel standard attribute itself is left in place.
                val inputMessagesAttr = span.attributes.filterIsInstance<GenAIAttributes.Input.Messages>().firstOrNull()
                inputMessagesAttr?.messages?.forEachIndexed { index, message ->
                    applyPromptAttributes(span, index, message)
                }
            }
            else -> {}
        }
    }

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INFERENCE -> {
                // Convert token attributes to Weave format
                span.replaceAttributes<GenAIAttributes.Usage.InputTokens> { attribute ->
                    CustomAttribute("gen_ai.usage.prompt_tokens", attribute.value)
                }

                span.replaceAttributes<GenAIAttributes.Usage.OutputTokens> { attribute ->
                    CustomAttribute("gen_ai.usage.completion_tokens", attribute.value)
                }

                // Decompose `gen_ai.output.messages` into Weave-shaped indexed completion attributes.
                // The OTel standard attribute itself is left in place.
                val outputMessagesAttr = span.attributes.filterIsInstance<GenAIAttributes.Output.Messages>().firstOrNull()
                outputMessagesAttr?.messages?.forEachIndexed { index, message ->
                    applyCompletionAttributes(span, index, message)
                }
            }
            else -> {}
        }
    }

    //region Private Methods

    private fun applyPromptAttributes(span: GenAIAgentSpan, index: Int, message: Message) {
        when (message) {
            is Message.System,
            is Message.User,
            is Message.Assistant,
            is Message.Reasoning -> {
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.content", HiddenString(message.content)))
            }

            is Message.Tool.Call -> {
                // Weave forces the role to `assistant` for tool-call entries in the prompt history,
                // and fans out the tool call into per-field indexed attributes.
                span.addAttribute(
                    CustomAttribute(
                        "gen_ai.prompt.$index.role",
                        Message.Role.Assistant.name.lowercase()
                    )
                )
                addToolCallAttributes(span, "gen_ai.prompt.$index.tool_calls", listOf(message))
                span.addAttribute(
                    CustomAttribute(
                        "gen_ai.prompt.$index.finish_reason",
                        GenAIAttributes.Response.FinishReasonType.ToolCalls.id
                    )
                )
            }

            is Message.Tool.Result -> {
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.content", HiddenString(message.content)))
                message.id?.let { id ->
                    span.addAttribute(CustomAttribute("gen_ai.prompt.$index.tool_call_id", id))
                }
            }
        }
    }

    private fun applyCompletionAttributes(span: GenAIAgentSpan, index: Int, message: Message) {
        when (message) {
            is Message.Assistant -> {
                span.addAttribute(CustomAttribute("gen_ai.completion.$index.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("gen_ai.completion.$index.content", HiddenString(message.content)))
                message.finishReason?.let { reason ->
                    span.addAttribute(CustomAttribute("gen_ai.completion.$index.finish_reason", reason))
                }
            }

            is Message.Reasoning -> {
                span.addAttribute(CustomAttribute("gen_ai.completion.$index.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("gen_ai.completion.$index.content", HiddenString(message.content)))
            }

            is Message.Tool.Call -> {
                // Weave expects `assistant` role for tool-call completions.
                span.addAttribute(
                    CustomAttribute(
                        "gen_ai.completion.$index.role",
                        Message.Role.Assistant.name.lowercase()
                    )
                )
                addToolCallAttributes(
                    span = span,
                    keyPrefix = "gen_ai.completion.$index.tool_calls",
                    toolCalls = listOf(message)
                )
                span.addAttribute(
                    CustomAttribute(
                        "gen_ai.completion.$index.finish_reason",
                        GenAIAttributes.Response.FinishReasonType.ToolCalls.id
                    )
                )
            }

            else -> {
                // Output messages should only be assistant/reasoning/tool-call responses.
            }
        }
    }

    /**
     * Fan tool-call entries out to indexed `<keyPrefix>.<toolIndex>.<field>` attributes:
     * - `<keyPrefix>.<j>.function` — `HiddenString` JSON of `{"name":..., "arguments":...}`
     * - `<keyPrefix>.<j>.id` — call id (plain string, used by Weave for tool-call/tool-result correlation)
     * - `<keyPrefix>.<j>.type` — `"function"` (constant, not sensitive)
     *
     * Content fields are wrapped in [HiddenString]; the standard `applyAttributes` pipeline handles
     * the verbose flag uniformly. The id is left plain so the UI can thread tool calls together
     * even when content is masked.
     */
    private fun addToolCallAttributes(
        span: GenAIAgentSpan,
        keyPrefix: String,
        toolCalls: List<Message.Tool.Call>,
    ) {
        toolCalls.forEachIndexed { toolIndex, tool ->
            // Match the legacy event-based output shape: `function` is a JSON string of {name,arguments}.
            val functionJson = JsonObject(
                mapOf(
                    "name" to JsonPrimitive(tool.tool),
                    "arguments" to JsonPrimitive(tool.content),
                )
            ).toString()
            span.addAttribute(CustomAttribute("$keyPrefix.$toolIndex.function", HiddenString(functionJson)))
            span.addAttribute(CustomAttribute("$keyPrefix.$toolIndex.id", tool.id ?: ""))
            span.addAttribute(CustomAttribute("$keyPrefix.$toolIndex.type", "function"))
        }
    }

    //endregion Private Methods
}
