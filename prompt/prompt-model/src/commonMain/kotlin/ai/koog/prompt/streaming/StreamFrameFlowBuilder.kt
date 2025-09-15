package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
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

private data class ToolCallState(
    val id: String?,
    val name: String?,
    val contents: String?
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> = Key

    companion object Key : CoroutineContext.Key<ToolCallState>
}

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
public class StreamFrameFlowBuilder(
    private val flowCollector: FlowCollector<StreamFrame>,
) {

    /**
     * Emits a [StreamFrame.Append] with the given [text].
     */
    public suspend fun append(text: String): CoroutineContext {
        tryEmitPendingToolCall()
        flowCollector.emitAppend(text)
        return coroutineContext.minusKey(ExpectingToolCallArguments.Key)
    }

    /**
     * Emits a [StreamFrame.End] with the given [finishReason].
     */
    public suspend fun end(finishReason: String?): CoroutineContext {
        tryEmitPendingToolCall()
        flowCollector.emitEnd(finishReason)
        return coroutineContext.minusKey(ExpectingToolCallArguments.Key)
    }

    private suspend fun tryEmitPendingToolCall() {
        val context = coroutineContext[ExpectingToolCallArguments.Key]
        if (context != null)
            flowCollector.emitToolCall(context.id, context.name ?: "", context.argumentsDelta ?: "")
    }

    /**
     * Updates the coroutine context to signal we're currently combining a tool call,
     * this does not emit anything yet, that only in [tryEmitPendingToolCall].
     */
    public suspend fun startOrCompleteToolCall(
        id: String?,
        name: String?,
        argumentsDelta: String? = null
    ): CoroutineContext {
        val context = coroutineContext[ExpectingToolCallArguments.Key]
        return if (context == null) {
            if (id == null)
                error("No tool call is in progress, and no tool call id was provided.")
            ExpectingToolCallArguments(id, name, argumentsDelta)
        } else {
            if (id != null && id != context.id)
                error("Tool call id mismatch. Expected ${context.id}, but received $id.")
            context.copy(argumentsDelta = (context.argumentsDelta ?: "") + argumentsDelta)
        }.let(coroutineContext::plus)
    }

    private data class ExpectingToolCallArguments(
        val id: String,
        val name: String?,
        val argumentsDelta: String?
    ) : CoroutineContext.Element {

        override val key: CoroutineContext.Key<*> = Key

        companion object Key : CoroutineContext.Key<ExpectingToolCallArguments>
    }
}
