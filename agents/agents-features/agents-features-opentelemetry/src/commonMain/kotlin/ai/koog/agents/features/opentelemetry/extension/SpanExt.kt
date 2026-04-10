package ai.koog.agents.features.opentelemetry.extension

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CommonAttributes
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.attribute.applyAttributes
import ai.koog.agents.features.opentelemetry.event.GenAIAgentEvent
import ai.koog.agents.features.opentelemetry.platform.errorTypeName
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.http.client.KoogHttpClientException
import ai.koog.prompt.message.Message
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.StatusData
import kotlinx.serialization.json.JsonElement

internal fun Span.setSpanStatus(endStatus: StatusData? = null) {
    setStatus(endStatus ?: StatusData.Ok)
}

internal fun Span.setAttributes(attributes: List<Attribute>, verbose: Boolean) {
    applyAttributes(attributes, verbose)
}

internal fun Span.setEvents(events: List<GenAIAgentEvent>, verbose: Boolean) {
    events.forEach { event ->
        val eventAttributes = buildList {
            event.bodyFieldsToAttributes(verbose)
            addAll(event.attributes)
        }

        addEvent(event.name) {
            applyAttributes(eventAttributes, verbose)
        }
    }
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
 * Returns the last [Message.Response] in this list, or `null` if no response has been produced yet.
 */
internal fun List<Message>.lastResponse(): Message.Response? =
    filterIsInstance<Message.Response>().lastOrNull()

/**
 * Sum of `metaInfo.inputTokensCount` across all [Message.Response] entries, for the `gen_ai.usage.input_tokens` attribute.
 */
internal fun List<Message>.sumInputTokens(): Int =
    filterIsInstance<Message.Response>().sumOf { it.metaInfo.inputTokensCount ?: 0 }

/**
 * Sum of `metaInfo.outputTokensCount` across all [Message.Response] entries, for the `gen_ai.usage.output_tokens` attribute.
 */
internal fun List<Message>.sumOutputTokens(): Int =
    filterIsInstance<Message.Response>().sumOf { it.metaInfo.outputTokensCount ?: 0 }

/**
 * Merges the per-response `metaInfo.metadata` maps into a single flat map, for the `gen_ai.response.metadata` attribute.
 * Later responses overwrite earlier ones on key collision.
 */
internal fun List<Message>.mergedResponseMetadata(): Map<String, JsonElement> =
    filterIsInstance<Message.Response>()
        .mapNotNull { it.metaInfo.metadata }
        .fold(mutableMapOf()) { acc, m -> acc.apply { putAll(m) } }

/**
 * Maps a [Message.Response] to its `FinishReasonType` for the `gen_ai.response.finish_reasons` attribute.
 * Exhaustive by design (no `else`) so a new [Message.Response] subtype surfaces as a compiler error.
 */
internal fun Message.Response.toFinishReason(): GenAIAttributes.Response.FinishReasonType =
    when (this) {
        is Message.Assistant,
        is Message.Reasoning -> {
            GenAIAttributes.Response.FinishReasonType.Stop
        }
        is Message.Tool.Call -> {
            GenAIAttributes.Response.FinishReasonType.ToolCalls
        }
    }
