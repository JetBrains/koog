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
 * Create a [Flow] of [StreamFrame.Append] objects from a list of [String] content.
 */
public fun streamFrameFlowOf(vararg content: String): Flow<StreamFrame.Append> =
    content.asFlow().map(StreamFrame::Append)

/**
 * Builds a [Flow] of [StreamFrame] objects.
 *
 * @see emitAppend for emitting a [StreamFrame.Append] object.
 * @see emitToolCall for emitting a [StreamFrame.ToolCall] object.
 * @see emitEnd for emitting a [StreamFrame.End] object.
 */
@OptIn(ExperimentalTypeInference::class)
public fun streamFrameFlow(@BuilderInference block: suspend FlowCollector<StreamFrame>.() -> Unit): Flow<StreamFrame> =
    flow(block)

/**
 * Emits a [StreamFrame.Append] with the given [text].
 */
public suspend fun FlowCollector<StreamFrame>.emitAppend(text: String): Unit =
    emit(StreamFrame.Append(text))

/**
 * Emits a [StreamFrame.End] with the given [finishReason].
 */
public suspend fun FlowCollector<StreamFrame>.emitEnd(
    finishReason: String? = null,
    metaInfo: ResponseMetaInfo? = null
): Unit =
    emit(StreamFrame.End(finishReason, metaInfo ?: ResponseMetaInfo.Empty))

/**
 * Emits a [StreamFrame.ToolCall] with the given [id], [name] and [content].
 */
public suspend fun FlowCollector<StreamFrame>.emitToolCall(id: String?, name: String, content: String): Unit =
    emit(StreamFrame.ToolCall(id, name, content))

/**
 * Builds a [Flow] of [StreamFrame] objects.
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

    /**
     * Emits a [StreamFrame.Append] with the given [text].
     */
    public suspend fun emitAppend(text: String) {
        tryEmitPendingToolCall()
        flowCollector.emitAppend(text)
    }

    /**
     * Emits a [StreamFrame.End] with the given [finishReason].
     */
    public suspend fun emitEnd(finishReason: String? = null, metaInfo: ResponseMetaInfo? = null) {
        tryEmitPendingToolCall()
        flowCollector.emitEnd(finishReason, metaInfo)
    }

    /**
     * Emits a [pendingToolCallRef] if it exists and then clears it.
     */
    public suspend fun tryEmitPendingToolCall() {
        val pendingToolCall = pendingToolCallRef.exchange(null)
        if (pendingToolCall != null) {
            flowCollector.emitToolCall(
                id = pendingToolCall.id,
                name = pendingToolCall.name ?: "",
                content = pendingToolCall.argumentsDelta ?: "{}"
            )
        }
    }

    /**
     * Updates the coroutine context to signal we're currently combining a tool call,
     * this does not emit anything yet, that happens only in [tryEmitPendingToolCall].
     *
     * @throws StreamFrameFlowBuilderError if there is
     */
    public suspend fun upsertToolCall(
        index: Int,
        id: String? = null,
        name: String? = null,
        args: String? = null
    ) {
        val new: PendingToolCall = if (id != null) {
            tryEmitPendingToolCall()
            PendingToolCall(index, id, name, args)
        } else {
            val previous: PendingToolCall? = pendingToolCallRef.load()
            when {
                previous == null ->
                    throw StreamFrameFlowBuilderError.NoPartialToolCallToComplete

                previous.index != index ->
                    throw StreamFrameFlowBuilderError.UnexpectedPartialToolCallIndex(previous.index, index)

                else ->
                    previous.appendArgumentsDelta(args)
            }
        }
        pendingToolCallRef.store(new)
    }

    private data class PendingToolCall(
        val index: Int,
        val id: String,
        val name: String?,
        val argumentsDelta: String?
    ) {
        fun appendArgumentsDelta(argumentsDelta: String?): PendingToolCall =
            copy(argumentsDelta = (this.argumentsDelta ?: "") + argumentsDelta)
    }
}
