package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class StreamFrameFlowBuilderTest {

    private val weatherCallId = "call_get_weather_1"
    private val weatherFunName = "get_weather"
    private val weatherArgList = listOf("{\"", "location", "\":\"", "Netherlands", "\"}")
    private val weatherArgString = weatherArgList.joinToString("")

    @Test
    fun `combine partial tool calls and manually emit as full tool call`() = runTest {
        buildStreamFrameFlow {
            upsertWeatherToolCallParts(0)
            tryEmitPendingToolCall()
        } assertContentEquals {
            emitWeatherToolCall()
        }
    }

    @Test
    fun `combine partial tool calls and automatically emit on append text`() = runTest {
        buildStreamFrameFlow {
            upsertWeatherToolCallParts(0)
            emitAppend("emitted tool?")
        } assertContentEquals {
            emitWeatherToolCall()
            emitAppend("emitted tool?")
        }
    }

    @Test
    fun `combine partial tool calls and automatically emit on end`() = runTest {
        buildStreamFrameFlow {
            upsertWeatherToolCallParts(0)
            emitEnd()
        } assertContentEquals {
            emitWeatherToolCall()
            emitEnd()
        }
    }

    @Test
    fun `combine partial tool calls and automatically emit on other tool call`() = runTest {
        buildStreamFrameFlow {
            upsertToolCall(index = 0, id = "some_other_id", name = "some_other_tool", args = "")
            upsertWeatherToolCallParts(index = 1)
            tryEmitPendingToolCall()
        } assertContentEquals {
            emitToolCall(id = "some_other_id", name = "some_other_tool", content = "")
            emitWeatherToolCall()
        }
    }

    @Test
    fun `throw when upserting partial tool call without an id`() = runTest {
        assertFailsWith<StreamFrameFlowBuilderError.NoPartialToolCallToComplete> {
            buildStreamFrameFlow {
                upsertToolCall(index = 0, id = null, name = "test_error", "")
            }.collect()
        }
    }

    @Test
    fun `throw when upserting partial tool call with index mismatch`() = runTest {
        assertFailsWith<StreamFrameFlowBuilderError.UnexpectedPartialToolCallIndex> {
            buildStreamFrameFlow {
                upsertToolCall(index = 0, id = "test", name = "test_error", "")
                upsertToolCall(index = 1, id = null, name = "test_error", "")
            }.collect()
        }
    }

    private suspend fun StreamFrameFlowBuilder.upsertWeatherToolCallParts(index: Int) {
        upsertToolCall(index = index, id = weatherCallId, name = weatherFunName, args = "")
        weatherArgList.forEach {
            upsertToolCall(index = index, args = it)
        }
    }

    private suspend fun FlowCollector<StreamFrame>.emitWeatherToolCall() =
        emitToolCall(id = weatherCallId, name = weatherFunName, content = weatherArgString)
}

private suspend infix fun Flow<StreamFrame>.assertContentEquals(expected: suspend FlowCollector<StreamFrame>.() -> Unit) =
    assertContentEquals(
        expected = flow(expected).toList(),
        actual = toList()
    )
