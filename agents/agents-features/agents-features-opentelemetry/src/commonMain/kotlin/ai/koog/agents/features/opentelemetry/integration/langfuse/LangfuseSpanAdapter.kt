package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.Attribute
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
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement

/**
 * Internal adapter class for enhancing and modifying spans related to GenAI agent processing.
 * It reshapes inference-span content from the OTel-standard `gen_ai.input.messages` /
 * `gen_ai.output.messages` attributes into the `gen_ai.prompt` / `gen_ai.completion` attributes.
 * Langfuse reads those ahead of the semconv message attributes and renders them in the OpenAI message shape.
 *
 * Each of the two attributes holds the whole message list as one JSON array in the OpenAI message shape.
 * One attribute per list keeps a long tool-call history within the span attribute count limit,
 * which is 128 by default. The original attributes remain on the span untouched.
 *
 * Langfuse v4 is observations-first: evaluators and filters read each observation on its own, and the
 * request and response of the whole run belong on the root observation. Koog's trace root is the
 * `create_agent` span, which carries neither, so the adapter copies the `invoke_agent` input and output
 * onto that parent as `langfuse.observation.input` / `langfuse.observation.output`, in the same OpenAI
 * message shape. The parent flushes its attributes only when it ends, which happens after the `invoke_agent` span.
 *
 * Langfuse also resolves the session of each observation on its own. The GenAI semantic conventions define no
 * `gen_ai.conversation.id` on the `create_agent` span, so the adapter sets `langfuse.session.id` on that root
 * observation to the run id, unless a `langfuse.session.id` trace attribute already set it.
 *
 * Node and subgraph spans keep their input and output under Koog-specific `koog.node.*` / `koog.subgraph.*`
 * attributes, which Langfuse does not read, so the adapter also copies them to `langfuse.observation.input` /
 * `langfuse.observation.output`. Without the copy, these observations show an empty input and output.
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
                    span.addAttribute(CustomAttribute(SESSION_ID_KEY, runId))
                    // The `create_agent` root has no `gen_ai.conversation.id` of its own, as the GenAI semantic
                    // conventions define none on it. A session from the trace attributes is already on the root and wins.
                    span.copyToRootObservation(SESSION_ID_KEY, runId, overwrite = false)
                }

                // The run input belongs on the trace root observation in Langfuse v4.
                span.attributes.filterIsInstance<GenAIAttributes.Input.Messages>().firstOrNull()?.let { attribute ->
                    val entries = JsonArray(attribute.messages.flatMap(::promptEntries))
                    span.copyToRootObservation(OBSERVATION_INPUT_KEY, HiddenString(entries.toString()))
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

                span.copyToObservation<KoogAttributes.Koog.Node.Input>(OBSERVATION_INPUT_KEY)
            }

            SpanType.SUBGRAPH -> {
                span.copyToObservation<KoogAttributes.Koog.Subgraph.Input>(OBSERVATION_INPUT_KEY)
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
                    usageDetails(messages.filterIsInstance<Message.Assistant>().firstOrNull()?.metaInfo)?.let { details ->
                        span.addAttribute(CustomAttribute(USAGE_DETAILS_KEY, details.toString()))
                    }
                }
            }

            SpanType.INVOKE_AGENT -> {
                // The run output belongs on the trace root observation in Langfuse v4.
                span.attributes.filterIsInstance<GenAIAttributes.Output.Messages>().firstOrNull()?.let { attribute ->
                    val responses = attribute.messages.filterIsInstance<Message.Assistant>()
                    val entries = JsonArray(responses.map(::completionEntry))
                    span.copyToRootObservation(OBSERVATION_OUTPUT_KEY, HiddenString(entries.toString()))
                }
            }

            SpanType.NODE -> {
                span.copyToObservation<KoogAttributes.Koog.Node.Output>(OBSERVATION_OUTPUT_KEY)
            }

            SpanType.SUBGRAPH -> {
                span.copyToObservation<KoogAttributes.Koog.Subgraph.Output>(OBSERVATION_OUTPUT_KEY)
            }

            else -> {}
        }
    }

    //region Private Methods

    /**
     * Writes [value] under [key] on the `create_agent` parent of this span, the trace root. Langfuse v4 reads the
     * request and response of a run from the root observation, which the `create_agent` span does not carry itself.
     * Guarded on the parent type, so a change in the span hierarchy degrades to a no-op rather than writing
     * to the wrong span. With [overwrite] set to `false`, a value already on the root is kept.
     */
    private fun GenAIAgentSpan.copyToRootObservation(key: String, value: Any, overwrite: Boolean = true) {
        val root = parentSpan?.takeIf { parent -> parent.type == SpanType.CREATE_AGENT } ?: return
        if (!overwrite && root.attributes.any { attribute -> attribute.key == key }) return
        root.addAttribute(CustomAttribute(key, value))
    }

    /**
     * Copies the value of the Koog-specific attribute [T] to [key] on the same span. Langfuse maps only a fixed list
     * of attribute keys onto the input and output of an observation, and none of the `koog.*` keys is on it.
     */
    private inline fun <reified T : Attribute> GenAIAgentSpan.copyToObservation(key: String) {
        attributes.filterIsInstance<T>().firstOrNull()?.let { attribute ->
            addAttribute(CustomAttribute(key, attribute.value))
        }
    }

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

    /**
     * Builds the Langfuse `usage_details` for a response that reports cached or reasoning tokens in its
     * metadata. Langfuse prices `input_cached_tokens` and `output_reasoning` apart from `input` and `output`,
     * and the `langfuse.observation.usage_details` attribute takes precedence over `gen_ai.usage.*`.
     * Google counts cached tokens inside the prompt token count, so they are subtracted from `input`,
     * and counts thoughts apart from the candidate tokens. Returns null when neither count is present.
     */
    private fun usageDetails(metaInfo: ResponseMetaInfo?): JsonObject? {
        val metadata = metaInfo?.metadata ?: return null
        val cached = metadata[CACHED_TOKENS_KEY]?.jsonPrimitive?.intOrNull
        val reasoning = metadata[REASONING_TOKENS_KEY]?.jsonPrimitive?.intOrNull
        if (cached == null && reasoning == null) return null
        return buildJsonObject {
            metaInfo.inputTokensCount?.let { put("input", it - (cached ?: 0)) }
            cached?.let { put("input_cached_tokens", it) }
            metaInfo.outputTokensCount?.let { put("output", it) }
            reasoning?.let { put("output_reasoning", it) }
        }
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
        const val USAGE_DETAILS_KEY = "langfuse.observation.usage_details"
        const val SESSION_ID_KEY = "langfuse.session.id"
        const val OBSERVATION_INPUT_KEY = "langfuse.observation.input"
        const val OBSERVATION_OUTPUT_KEY = "langfuse.observation.output"
        const val CACHED_TOKENS_KEY = "cachedContentTokenCount"
        const val REASONING_TOKENS_KEY = "thoughtsTokenCount"
    }
}
