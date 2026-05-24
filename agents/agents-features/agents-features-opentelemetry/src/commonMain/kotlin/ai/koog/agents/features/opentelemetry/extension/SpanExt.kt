package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.applyAttributes
import ai.koog.agents.features.opentelemetry.platform.errorTypeName
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.StatusData
import kotlinx.serialization.json.JsonElement

internal fun Span.setSpanStatus(endStatus: StatusData? = null) {
    setStatus(endStatus ?: StatusData.Ok)
}

internal fun Span.setAttributes(attributes: List<Attribute>, verbose: Boolean) {
    applyAttributes(attributes, verbose)
}

internal fun Throwable?.toStatusData(): StatusData =
    if (this == null) StatusData.Ok else StatusData.Error(this.message)

/**
 * Check if an error is defined and add common error attributes to the span if not null.
 */
internal fun GenAIAgentSpan.addCommonErrorAttributes(error: Throwable?) {
    error?.let {
        addAttribute(CommonAttributes.Error.Type(error.extractActualErrorType()))
    }
}

private fun Throwable.extractActualErrorType(): String {
    val cause = this.cause
    return when {
        this is KoogHttpClientException -> "${this::class.simpleName}-$clientName-httpCode=$statusCode"
        cause is KoogHttpClientException -> "${cause::class.simpleName}-${cause.clientName}-httpCode=${cause.statusCode}"
        cause != null -> "${this::class.simpleName}-${cause::class.simpleName}"
        else -> errorTypeName(this) ?: (this::class.simpleName ?: "UnknownError")
    }
}

/**
 * Returns the [Message.System] messages in this list, for the `gen_ai.system_instructions` attribute.
 */
internal fun List<Message>.systemMessages(): List<Message.System> =
    filterIsInstance<Message.System>()

/**
 * Returns the last [Message.Assistant] in this list, or `null` if no response has been produced yet.
 */
internal fun List<Message>.lastAssistant(): Message.Assistant? =
    filterIsInstance<Message.Assistant>().lastOrNull()

/**
 * Sum of `metaInfo.inputTokensCount` across all [Message.Assistant] entries, for the `gen_ai.usage.input_tokens` attribute.
 */
internal fun List<Message>.sumInputTokens(): Int =
    filterIsInstance<Message.Assistant>().sumOf { it.metaInfo.inputTokensCount ?: 0 }

/**
 * Sum of `metaInfo.outputTokensCount` across all [Message.Assistant] entries, for the `gen_ai.usage.output_tokens` attribute.
 */
internal fun List<Message>.sumOutputTokens(): Int =
    filterIsInstance<Message.Assistant>().sumOf { it.metaInfo.outputTokensCount ?: 0 }

/**
 * Merges the per-response `metaInfo.metadata` maps into a single flat map, for the `gen_ai.response.metadata` attribute.
 * Later responses overwrite earlier ones on key collision.
 */
internal fun List<Message>.mergedAssistantMetadata(): Map<String, JsonElement> =
    filterIsInstance<Message.Assistant>()
        .mapNotNull { it.metaInfo.metadata }
        .fold(mutableMapOf()) { acc, m -> acc.apply { putAll(m) } }

/**
 * Maps a [Message.Assistant] to its `FinishReasonType` for the `gen_ai.response.finish_reasons` attribute.
 * Exhaustive by design (no `else`) so a new [Message.Assistant] subtype surfaces as a compiler error.
 */
internal fun Message.Assistant.toFinishReason(): GenAIAttributes.Response.FinishReasonType =
    if (parts.any { it is MessagePart.Tool.Call }) {
        GenAIAttributes.Response.FinishReasonType.ToolCalls
    } else {
        GenAIAttributes.Response.FinishReasonType.Stop
    }
