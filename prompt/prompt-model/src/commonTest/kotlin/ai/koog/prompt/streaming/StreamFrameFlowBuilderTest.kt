package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class StreamFrameFlowBuilderTest {

    private val weatherCallId = "call_get_weather_1"
    private val weatherFunName = "get_weather"
    private val weatherArgList = listOf("{\"", "location", "\":\"", "Netherlands", "\"}")
    private val weatherArgString = weatherArgList.joinToString("")

    @Test
    fun testCombiningOfPartialToolCallsWithManualEmit() = runTest {
        buildStreamFrameFlow {
            appendWeatherToolAsParts(0)
            tryEmitPendingToolCall()
        } assertContentEquals {
            emitWeatherToolCall()
        }
    }

    @Test
    fun testCombiningOfPartialToolCallsWithAutomaticEmitOnAppend() = runTest {
        buildStreamFrameFlow {
            appendWeatherToolAsParts(0)
            emitAppend("emitted tool?")
        } assertContentEquals {
            emitWeatherToolCall()
            emitAppend("emitted tool?")
        }
    }

    @Test
    fun testCombiningOfPartialToolCallsWithAutomaticEmitOnEnd() = runTest {
        buildStreamFrameFlow {
            appendWeatherToolAsParts(0)
            emitEnd()
        } assertContentEquals {
            emitWeatherToolCall()
            emitEnd()
        }
    }

    @Test
    fun testAutomaticEmitOnNewToolCallId() = runTest {
        buildStreamFrameFlow {
            appendToolCall(index = 0, id = "some_other_id", name = "some_other_tool", args = "")
            appendWeatherToolAsParts(index = 1)
            tryEmitPendingToolCall()
        } assertContentEquals {
            emitToolCall(id = "some_other_id", name = "some_other_tool", content = "")
            emitWeatherToolCall()
        }
    }


    private suspend fun StreamFrameFlowBuilder.appendWeatherToolAsParts(index: Int) {
        appendToolCall(index = index, id = weatherCallId, name = weatherFunName, args = "")
        weatherArgList.forEach {
            appendToolCall(index = index, args = it)
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
