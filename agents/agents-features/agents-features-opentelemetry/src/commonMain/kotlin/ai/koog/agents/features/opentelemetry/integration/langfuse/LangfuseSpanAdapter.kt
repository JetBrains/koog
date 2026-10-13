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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Internal adapter class for enhancing and modifying spans related to GenAI agent processing.
 * It reshapes inference-span content from the OTel-standard `gen_ai.input.messages` /
 * `gen_ai.output.messages` attributes (which Langfuse does not currently recognize) into the
 * `gen_ai.prompt` / `gen_ai.completion` attributes that Langfuse displays.
 *
 * Each of the two attributes holds the whole message list as one JSON array in the OpenAI message shape.
 * One attribute per list keeps a long tool-call history within the span attribute count limit,
 * which is 128 by default. The original attributes remain on the span untouched.
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
                // Rewrite `gen_ai.input.messages` as the Langfuse-shaped `gen_ai.prompt` attribute.
                // The OTel standard `gen_ai.input.messages` attribute itself is left in place, so
                // backends that recognize the modern semconv still see it.
                val inputMessagesAttr = span.attributes.filterIsInstance<GenAIAttributes.Input.Messages>().firstOrNull()
                inputMessagesAttr?.messages?.let { messages ->
                    val entries = JsonArray(messages.flatMap(::promptEntries))
                    span.addAttribute(CustomAttribute(PROMPT_KEY, HiddenString(entries.toString())))
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
                // Rewrite `gen_ai.output.messages` as the Langfuse-shaped `gen_ai.completion` attribute.
                // The OTel standard attribute itself is left in place.
                val outputMessagesAttr = span.attributes.filterIsInstance<GenAIAttributes.Output.Messages>().firstOrNull()
                outputMessagesAttr?.messages?.let { messages ->
                    val entries = JsonArray(messages.filterIsInstance<Message.Assistant>().map(::completionEntry))
                    span.addAttribute(CustomAttribute(COMPLETION_KEY, HiddenString(entries.toString())))
                }
            }
            else -> {}
        }
    }

    //region Private Methods

    private fun promptEntries(message: Message): List<JsonObject> = when (message) {
        is Message.System -> listOf(textEntry(message.role, message.parts.joinToString("\n") { it.text }))

        is Message.User -> {
            val toolResults = message.parts.filterIsInstance<MessagePart.Tool.Result>()
            val textParts = message.parts.filterIsInstance<MessagePart.Text>()
            val toolEntries = toolResults.map { toolResult ->
                buildJsonObject {
                    put("role", "tool")
                    toolResult.id?.let { id -> put("tool_call_id", id) }
                    put("content", toolResult.output)
                }
            }
            if (toolResults.isNotEmpty() && textParts.isEmpty()) {
                toolEntries
            } else {
                toolEntries + textEntry(message.role, textParts.joinToString("\n") { it.text })
            }
        }

        is Message.Assistant -> listOf(assistantEntry(message))
    }

    private fun completionEntry(message: Message.Assistant): JsonObject {
        val finishReason = if (message.parts.any { it is MessagePart.Tool.Call }) {
            GenAIAttributes.Response.FinishReasonType.ToolCalls.id
        } else {
            message.finishReason
        }
        val entry = assistantEntry(message)
        return finishReason?.let { reason -> JsonObject(entry + ("finish_reason" to JsonPrimitive(reason))) } ?: entry
    }

    private fun textEntry(role: Message.Role, text: String): JsonObject = buildJsonObject {
        put("role", role.name.lowercase())
        put("content", text)
    }

    /**
     * Builds an assistant message in the OpenAI message shape that Langfuse renders.
     * The text goes to `content` and the tool calls to `tool_calls`. When the message carries readable
     * reasoning, `content` becomes a list of typed `reasoning` and `text` blocks in the original order,
     * so the reasoning stays apart from the answer.
     */
    private fun assistantEntry(message: Message.Assistant): JsonObject = buildJsonObject {
        val reasoningParts = message.parts.reasoningWithContent()
        val textParts = message.parts.filterIsInstance<MessagePart.Text>()
        val toolCalls = message.parts.filterIsInstance<MessagePart.Tool.Call>()

        put("role", Message.Role.Assistant.name.lowercase())
        if (reasoningParts.isNotEmpty()) {
            putJsonArray("content") {
                message.parts.forEach { part ->
                    when (part) {
                        is MessagePart.Reasoning -> if (part in reasoningParts) {
                            addJsonObject {
                                put("type", "reasoning")
                                put("text", part.content.joinToString("\n"))
                            }
                        }

                        is MessagePart.Text -> addJsonObject {
                            put("type", "text")
                            put("text", part.text)
                        }

                        else -> {}
                    }
                }
            }
        } else if (textParts.isNotEmpty() || toolCalls.isEmpty()) {
            put("content", textParts.joinToString("\n") { it.text })
        }

        if (toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                toolCalls.forEach { toolCall ->
                    addJsonObject {
                        toolCall.id?.let { id -> put("id", id) }
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", toolCall.tool)
                            put("arguments", toolCall.args)
                        }
                    }
                }
            }
        }
    }

    /**
     * Reasoning parts that carry text. Signature-only parts (Google sends an empty list,
     * Anthropic sends a single empty string) must not suppress the text parts of the same message.
     */
    private fun List<MessagePart>.reasoningWithContent(): List<MessagePart.Reasoning> =
        filterIsInstance<MessagePart.Reasoning>().filter { part -> part.content.any { it.isNotBlank() } }

    //endregion Private Methods

    private companion object {
        const val PROMPT_KEY = "gen_ai.prompt"
        const val COMPLETION_KEY = "gen_ai.completion"
    }
}
