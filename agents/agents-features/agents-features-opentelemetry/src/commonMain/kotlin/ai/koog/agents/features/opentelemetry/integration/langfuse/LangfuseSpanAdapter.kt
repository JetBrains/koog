package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.KoogAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.integration.SpanAdapter
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.SpanType
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Internal adapter class for enhancing and modifying spans related to GenAI agent processing.
 * It reshapes inference-span content from the OTel-standard `gen_ai.input.messages` /
 * `gen_ai.output.messages` attributes (which Langfuse does not currently recognize) into the
 * indexed `gen_ai.prompt.{i}.*` / `gen_ai.completion.{i}.*` attributes that Langfuse displays.
 *
 * The adapter consumes the typed `messages: List<Message>` exposed by those standard attributes
 * and re-emits them in the indexed shape; the original attributes remain on the span untouched.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class LangfuseSpanAdapter(
    private val traceAttributes: List<CustomAttribute>,
    @Suppress("unused")
    private val openTelemetryConfig: OpenTelemetryConfig,
) : SpanAdapter() {

    private val stepKey = AtomicInt(0)

    override fun onBeforeSpanStarted(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INVOKE_AGENT -> {
                val runId =
                    span.attributes.find { attribute -> attribute.key == GenAIAttributes.Conversation.Id("").key }?.value

                runId?.let { runId ->
                    span.addAttribute(CustomAttribute("langfuse.session.id", runId))
                }
            }

            SpanType.INFERENCE -> {
                // Decompose `gen_ai.input.messages` into Langfuse-shaped indexed prompt attributes.
                // The OTel standard `gen_ai.input.messages` attribute itself is left in place, so
                // backends that recognize the modern semconv still see it.
                val inputMessagesAttr = span.attributes.filterIsInstance<GenAIAttributes.Input.Messages>().firstOrNull()
                inputMessagesAttr?.messages?.fold(0) { index, message ->
                    applyPromptAttributes(span, index, message)
                }
            }

            SpanType.NODE -> {
                val step = stepKey.fetchAndIncrement()

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

        // Adding attributes to all spans as per Langfuse recommendation for OTel instrumentation:
        // https://langfuse.com/integrations/native/opentelemetry#propagating-attributes
        traceAttributes.forEach { attribute ->
            span.addAttribute(attribute)
        }
    }

    override fun onBeforeSpanFinished(span: GenAIAgentSpan) {
        when (span.type) {
            SpanType.INFERENCE -> {
                // Decompose `gen_ai.output.messages` into Langfuse-shaped indexed completion attributes.
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

    /**
     * Writes [message] as prompt entries starting at [index] and returns the index after the last entry.
     * A user message with several tool results becomes one `tool` entry per result.
     */
    private fun applyPromptAttributes(span: GenAIAgentSpan, index: Int, message: Message): Int {
        val prefix = "gen_ai.prompt.$index"
        when (message) {
            is Message.System -> {
                span.addAttribute(CustomAttribute("$prefix.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("$prefix.content", HiddenString(message.parts.joinToString("\n") { it.text })))
            }

            is Message.User -> {
                val toolResults = message.parts.filterIsInstance<MessagePart.Tool.Result>()
                val textParts = message.parts.filterIsInstance<MessagePart.Text>()

                toolResults.forEachIndexed { offset, toolResult ->
                    val toolPrefix = "gen_ai.prompt.${index + offset}"
                    span.addAttribute(CustomAttribute("$toolPrefix.role", "tool"))
                    toolResult.id?.let { id -> span.addAttribute(CustomAttribute("$toolPrefix.tool_call_id", id)) }
                    span.addAttribute(CustomAttribute("$toolPrefix.content", HiddenString(toolResult.output)))
                }
                if (toolResults.isNotEmpty() && textParts.isEmpty()) {
                    return index + toolResults.size
                }

                val userPrefix = "gen_ai.prompt.${index + toolResults.size}"
                span.addAttribute(CustomAttribute("$userPrefix.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("$userPrefix.content", HiddenString(textParts.joinToString("\n") { it.text })))
                return index + toolResults.size + 1
            }

            is Message.Assistant -> {
                span.addAttribute(CustomAttribute("$prefix.role", message.role.name.lowercase()))
                applyAssistantParts(span, prefix, message)
            }
        }
        return index + 1
    }

    private fun applyCompletionAttributes(span: GenAIAgentSpan, index: Int, message: Message) {
        val prefix = "gen_ai.completion.$index"
        // Langfuse expects `assistant` for completion entries.
        span.addAttribute(CustomAttribute("$prefix.role", Message.Role.Assistant.name.lowercase()))

        when (message) {
            is Message.Assistant -> {
                applyAssistantParts(span, prefix, message)

                val finishReason = if (message.parts.any { it is MessagePart.Tool.Call }) {
                    GenAIAttributes.Response.FinishReasonType.ToolCalls.id
                } else {
                    message.finishReason
                }
                finishReason?.let { reason ->
                    span.addAttribute(CustomAttribute("$prefix.finish_reason", reason))
                }
            }

            else -> {
                // Output messages should only be assistant responses.
            }
        }
    }

    /**
     * Writes every part of an assistant message in the OpenAI message shape that Langfuse renders.
     * The text goes to `content` and the tool calls to `tool_calls`. When the message carries readable
     * reasoning, `content` becomes a list of typed `reasoning` and `text` blocks in the original order,
     * so the reasoning stays apart from the answer.
     */
    private fun applyAssistantParts(span: GenAIAgentSpan, prefix: String, message: Message.Assistant) {
        val reasoningParts = message.parts.reasoningWithContent()
        val textParts = message.parts.filterIsInstance<MessagePart.Text>()
        val toolCalls = message.parts.filterIsInstance<MessagePart.Tool.Call>()

        if (reasoningParts.isNotEmpty()) {
            val blocks = message.parts.mapNotNull { part ->
                when (part) {
                    is MessagePart.Reasoning -> if (part in reasoningParts) "reasoning" to part.content.joinToString("\n") else null
                    is MessagePart.Text -> "text" to part.text
                    else -> null
                }
            }
            blocks.forEachIndexed { blockIndex, (type, text) ->
                span.addAttribute(CustomAttribute("$prefix.content.$blockIndex.type", type))
                span.addAttribute(CustomAttribute("$prefix.content.$blockIndex.text", HiddenString(text)))
            }
        } else if (textParts.isNotEmpty() || toolCalls.isEmpty()) {
            span.addAttribute(CustomAttribute("$prefix.content", HiddenString(textParts.joinToString("\n") { it.text })))
        }

        toolCalls.forEachIndexed { callIndex, toolCall ->
            val callPrefix = "$prefix.tool_calls.$callIndex"
            toolCall.id?.let { id -> span.addAttribute(CustomAttribute("$callPrefix.id", id)) }
            span.addAttribute(CustomAttribute("$callPrefix.type", "function"))
            span.addAttribute(CustomAttribute("$callPrefix.function.name", toolCall.tool))
            span.addAttribute(CustomAttribute("$callPrefix.function.arguments", HiddenString(toolCall.args)))
        }
    }

    /**
     * Reasoning parts that carry text. Signature-only parts (Google sends an empty list,
     * Anthropic sends a single empty string) must not suppress the text parts of the same message.
     */
    private fun List<MessagePart>.reasoningWithContent(): List<MessagePart.Reasoning> =
        filterIsInstance<MessagePart.Reasoning>().filter { part -> part.content.any { it.isNotBlank() } }

    //endregion Private Methods
}
