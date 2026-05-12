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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
                inputMessagesAttr?.messages?.forEachIndexed { index, message ->
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
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.role", message.role.name.lowercase()))
                span.addAttribute(
                    CustomAttribute(
                        "gen_ai.prompt.$index.content",
                        HiddenString(encodeToolCallsContent(listOf(message))),
                    )
                )
            }

            is Message.Tool.Result -> {
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.role", message.role.name.lowercase()))
                span.addAttribute(CustomAttribute("gen_ai.prompt.$index.content", HiddenString(message.content)))
            }
        }
    }

    private fun applyCompletionAttributes(span: GenAIAgentSpan, index: Int, message: Message) {
        // Langfuse expects `assistant` for completion entries (even when the underlying message is a Tool.Call).
        span.addAttribute(
            CustomAttribute("gen_ai.completion.$index.role", Message.Role.Assistant.name.lowercase())
        )

        when (message) {
            is Message.Assistant -> {
                span.addAttribute(CustomAttribute("gen_ai.completion.$index.content", HiddenString(message.content)))
                message.finishReason?.let { reason ->
                    span.addAttribute(CustomAttribute("gen_ai.completion.$index.finish_reason", reason))
                }
            }

            is Message.Reasoning -> {
                span.addAttribute(CustomAttribute("gen_ai.completion.$index.content", HiddenString(message.content)))
            }

            is Message.Tool.Call -> {
                span.addAttribute(
                    CustomAttribute(
                        "gen_ai.completion.$index.content",
                        HiddenString(encodeToolCallsContent(listOf(message))),
                    )
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

    //endregion Private Methods
}

/**
 * Encodes a list of tool-call messages into the raw JSON-array string used by [LangfuseSpanAdapter]
 * for the `gen_ai.prompt.{i}.content` / `gen_ai.completion.{i}.content` attribute when the underlying message
 * is a tool call.
 *
 * Each entry is shaped as `{"function": {"name": <name>, "arguments": <args>}, "id": <id>, "type": "function"}`.
 * Masking is the caller's responsibility — the caller wraps the result in [HiddenString] and the
 * standard `applyAttributes` pipeline handles the verbose flag uniformly with all other attributes.
 *
 * Marked `internal` so the [LangfuseSpanAdapter] tests can compute the identical expected value.
 */
internal fun encodeToolCallsContent(toolCalls: List<Message.Tool.Call>): String {
    val array = JsonArray(
        toolCalls.map { tool ->
            JsonObject(
                mapOf(
                    "function" to JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(tool.tool),
                            "arguments" to JsonPrimitive(tool.content),
                        )
                    ),
                    "id" to JsonPrimitive(tool.id ?: ""),
                    "type" to JsonPrimitive("function"),
                )
            )
        }
    )
    return array.toString()
}
