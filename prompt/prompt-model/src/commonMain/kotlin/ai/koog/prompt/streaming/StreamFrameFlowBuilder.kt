package ai.koog.prompt.streaming

import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalTypeInference

/**
 * Create a [Flow] of [StreamFrame.TextDelta] objects from a list of [String] content.
 */
public fun streamFrameFlowOf(vararg content: String): Flow<StreamFrame.TextDelta> =
    content.asFlow().map(StreamFrame::TextDelta)

/**
 * Builds a [Flow] of [StreamFrame] objects.
 *
 * @see emitTextDelta for emitting a [StreamFrame.TextDelta] object.
 * @see emitTextComplete for emitting a [StreamFrame.TextComplete] object.
 * @see emitReasoningDelta for emitting a [StreamFrame.ReasoningDelta] object.
 * @see emitReasoningComplete for emitting a [StreamFrame.ReasoningComplete] object.
 * @see emitToolCallDelta for emitting a [StreamFrame.ToolCallDelta] object.
 * @see emitToolCallComplete for emitting a [StreamFrame.ToolCallComplete] object.

 * @see emitEnd for emitting a [StreamFrame.End] object.
 */
@OptIn(ExperimentalTypeInference::class)
public fun streamFrameFlow(@BuilderInference block: suspend FlowCollector<StreamFrame>.() -> Unit): Flow<StreamFrame> =
    flow(block)

/**
 * Emits a [StreamFrame.TextDelta] with the given [text].
 */
public suspend fun FlowCollector<StreamFrame>.emitTextDelta(text: String, index: Int? = null): Unit =
    emit(StreamFrame.TextDelta(text, index))

/**
 * Emits a [StreamFrame.TextComplete] with the given [text].
 */
public suspend fun FlowCollector<StreamFrame>.emitTextComplete(text: String, index: Int? = null): Unit =
    emit(StreamFrame.TextComplete(text, index))

/**
 * Emits a [StreamFrame.ReasoningDelta] with the given [text] and [summary].
 */
public suspend fun FlowCollector<StreamFrame>.emitReasoningDelta(
    text: String? = null,
    summary: String? = null,
    index: Int? = null
): Unit =
    emit(StreamFrame.ReasoningDelta(text, summary, index))

/**
 * Emits a [StreamFrame.ReasoningComplete] with the given [text].
 */
public suspend fun FlowCollector<StreamFrame>.emitReasoningComplete(
    text: String,
    summary: String? = null,
    encrypted: String? = null,
    index: Int? = null
): Unit =
    emitReasoningComplete(listOf(text), summary?.let { listOf(it) }, encrypted, index)

/**
 * Emits a [StreamFrame.ReasoningComplete] with the given [text].
 */
public suspend fun FlowCollector<StreamFrame>.emitReasoningComplete(
    text: List<String>,
    summary: List<String>? = null,
    encrypted: String? = null,
    index: Int? = null
): Unit =
    emit(StreamFrame.ReasoningComplete(text, summary, encrypted, index))

/**
 * Emits a [StreamFrame.End] with the given [finishReason].
 */
public suspend fun FlowCollector<StreamFrame>.emitEnd(
    finishReason: String? = null,
    metaInfo: ResponseMetaInfo? = null
): Unit =
    emit(StreamFrame.End(finishReason, metaInfo ?: ResponseMetaInfo.Empty))

/**
 * Emits a [StreamFrame.ToolCallDelta] with the given [id], [name] and [content].
 */
public suspend fun FlowCollector<StreamFrame>.emitToolCallDelta(
    id: String?,
    name: String?,
    content: String?,
    index: Int? = null
): Unit =
    emit(StreamFrame.ToolCallDelta(id, name, content, index))

/**
 * Emits a [StreamFrame.ToolCallComplete] with the given [id], [name] and [content].
 */
public suspend fun FlowCollector<StreamFrame>.emitToolCallComplete(
    id: String?,
    name: String,
    content: String,
    index: Int? = null
): Unit =
    emit(StreamFrame.ToolCallComplete(id, name, content, index))

/**
 * Builds a [Flow] of [StreamFrame] objects.
 * Should be used only in case model does not produce completion events.
 */
public fun buildStreamFrameFlow(block: suspend StreamFrameFlowBuilder.() -> Unit): Flow<StreamFrame> =
    streamFrameFlow {
        val builder = StreamFrameFlowBuilder(this)
        block(builder)
    }

/**
 * Represents a wrapper around a [FlowCollector] that provides methods for emitting [StreamFrame] objects.
 *
 * This is mainly used for combining chunked tool calls and only emit completed tool calls.
 *
 * @property flowCollector The underlying [FlowCollector] used for emitting [StreamFrame] objects.
 */
@OptIn(ExperimentalAtomicApi::class)
public class StreamFrameFlowBuilder(
    private val flowCollector: FlowCollector<StreamFrame>,
) {

    private val pendingToolCallRef = AtomicReference<PendingToolCall?>(null)
    private val pendingTextRef = AtomicReference<PendingText?>(null)
    private val pendingReasoningRef = AtomicReference<PendingReasoning?>(null)

    /**
     * Emits a [StreamFrame.TextDelta] with the given [text].
     */
    public suspend fun emitTextDelta(text: String, index: Int? = null) {
        tryEmitPendingToolCall()
        tryEmitPendingReasoning()
        val previous: PendingText? = pendingTextRef.load()
        if (previous == null) {
            pendingTextRef.store(PendingText(textDelta = text, index = index))
        } else {
            pendingTextRef.store(previous.appendTextDelta(text, index))
        }
        flowCollector.emitTextDelta(text, index)
    }

    /**
     * Emits a [StreamFrame.ReasoningDelta] with the given [text].
     */
    public suspend fun emitReasoningDelta(text: String? = null, summary: String? = null, index: Int? = null) {
        tryEmitPendingToolCall()
        tryEmitPendingText()
        val previous: PendingReasoning? = pendingReasoningRef.load()
        if (previous == null) {
            pendingReasoningRef.store(PendingReasoning(textDelta = text, summaryDelta = summary, index = index))
        } else {
            pendingReasoningRef.store(previous.appendDelta(text, summary, index))
        }
        flowCollector.emitReasoningDelta(text, summary, index)
    }

    /**
     * Emits a [StreamFrame.End] with the given [finishReason].
     */
    public suspend fun emitEnd(finishReason: String? = null, metaInfo: ResponseMetaInfo? = null) {
        tryEmitPendingToolCall()
        tryEmitPendingText()
        tryEmitPendingReasoning()
        flowCollector.emitEnd(finishReason, metaInfo)
    }

    /**
     * Updates the coroutine context to signal we're currently combining a tool call,
     * this does not emit anything yet, that happens only in [tryEmitPendingToolCall].
     *
     * @throws StreamFrameFlowBuilderError if there is
     */
    public suspend fun emitToolCallDelta(
        id: String? = null,
        name: String? = null,
        args: String? = null,
        index: Int? = null
    ) {
        tryEmitPendingText()
        tryEmitPendingReasoning()
        val previous: PendingToolCall? = pendingToolCallRef.load()
        val new: PendingToolCall = if (id != null || index != previous?.index) {
            tryEmitPendingToolCall()
            PendingToolCall(id, name, args, index)
        } else {
            when {
                previous == null ->
                    throw StreamFrameFlowBuilderError.NoPartialToolCallToComplete()

                previous.index != index ->
                    throw StreamFrameFlowBuilderError.UnexpectedPartialToolCallIndex(previous.index, index)

                else ->
                    previous.appendArgumentsDelta(args)
            }
        }
        pendingToolCallRef.store(new)
        flowCollector.emitToolCallDelta(id, name, args, index)
    }

    /**
     * Emits a [pendingTextRef] if it exists and then clears it.
     */
    public suspend fun tryEmitPendingText() {
        val pendingText = pendingTextRef.exchange(null)
        if (pendingText != null) {
            flowCollector.emitTextComplete(
                text = pendingText.textDelta ?: "",
                index = pendingText.index
            )
        }
    }

    /**
     * Emits a [pendingReasoningRef] if it exists and then clears it.
     */
    public suspend fun tryEmitPendingReasoning() {
        val pendingReasoning = pendingReasoningRef.exchange(null)
        if (pendingReasoning != null) {
            flowCollector.emitReasoningComplete(
                text = pendingReasoning.textDelta ?: "",
                summary = pendingReasoning.summaryDelta,
                index = pendingReasoning.index
            )
        }
    }

    /**
     * Emits a [pendingToolCallRef] if it exists and then clears it.
     */
    public suspend fun tryEmitPendingToolCall() {
        val pendingToolCall = pendingToolCallRef.exchange(null)
        if (pendingToolCall != null) {
            flowCollector.emitToolCallComplete(
                id = pendingToolCall.id,
                name = pendingToolCall.name ?: "",
                content = pendingToolCall.argumentsDelta ?: "{}",
                index = pendingToolCall.index
            )
        }
    }

    private data class PendingToolCall(
        val id: String?,
        val name: String?,
        val argumentsDelta: String?,
        val index: Int?,
    ) {
        fun appendArgumentsDelta(argumentsDelta: String?): PendingToolCall {
            val newArgs =
                if (argumentsDelta == null) this.argumentsDelta else (this.argumentsDelta ?: "") + argumentsDelta
            return copy(argumentsDelta = newArgs)
        }
    }

    private data class PendingText(
        val textDelta: String?,
        val index: Int?,
    ) {
        fun appendTextDelta(textDelta: String?, index: Int?): PendingText {
            require(this.index == index)
            val newText = if (textDelta == null) this.textDelta else (this.textDelta ?: "") + textDelta
            return copy(textDelta = newText)
        }
    }

    private data class PendingReasoning(
        val textDelta: String?,
        val summaryDelta: String?,
        val index: Int?
    ) {
        fun appendDelta(textDelta: String?, summaryDelta: String?, index: Int?): PendingReasoning {
            require(this.index == index)
            val textDelta = if (textDelta == null) this.textDelta else (this.textDelta ?: "") + textDelta
            val summaryDelta = if (summaryDelta == null) this.summaryDelta else (this.summaryDelta ?: "") + summaryDelta
            return copy(textDelta = textDelta, summaryDelta = summaryDelta)
        }
    }
}
