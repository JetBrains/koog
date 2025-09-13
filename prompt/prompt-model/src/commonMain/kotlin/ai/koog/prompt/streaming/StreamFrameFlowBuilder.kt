package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
public suspend fun FlowCollector<StreamFrame>.emitEnd(finishReason: String? = null): Unit =
    emit(StreamFrame.End(finishReason))

/**
 * Emits a [StreamFrame.ToolCall] with the given [id], [name] and [content].
 */
public suspend fun FlowCollector<StreamFrame>.emitToolCall(id: String?, name: String, content: String): Unit =
    emit(StreamFrame.ToolCall(id, name, content))
