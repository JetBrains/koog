package ai.koog.agents.example.streaming

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.simpleapi.Switch
import ai.koog.agents.example.simpleapi.SwitchTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.toMessageResponse
import ai.koog.utils.io.use
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingAgentWithToolsTest {
    @Test
    fun testConsecutiveToolCallsPreserveAssistantHistoryAndStreamingEvents() = runTest {
        val switch = Switch()
        val firstResponse = listOf(
            StreamFrame.ReasoningComplete("reasoning-1", listOf("Check the switch first")),
            StreamFrame.TextComplete("Checking the switch"),
            StreamFrame.ToolCallComplete("call-1", "switchState", "{\"state\":false}"),
            StreamFrame.End("tool_calls")
        )
        val secondResponse = listOf(
            StreamFrame.ReasoningComplete("reasoning-2", listOf("Turn the switch on")),
            StreamFrame.TextComplete("Turning the switch on"),
            StreamFrame.ToolCallComplete("call-2", "switch", "{\"state\":true}"),
            StreamFrame.End("tool_calls")
        )
        val finalResponse = listOf(
            StreamFrame.TextDelta("The switch is on"),
            StreamFrame.TextComplete("The switch is on"),
            StreamFrame.End("stop")
        )
        val executor = getMockExecutor {
            mockLLMStream(firstResponse.asFlow()) onRequestEquals "Turn on the switch"
            mockLLMStream(secondResponse.asFlow()) onRequestContains "Switch is off"
            mockLLMStream(finalResponse.asFlow()) onRequestContains "Switched to on"
        }
        val requests = mutableListOf<Prompt>()
        val receivedFrames = mutableListOf<StreamFrame>()
        var completedStreams = 0
        var finalPrompt: Prompt? = null

        AIAgent(
            promptExecutor = executor,
            strategy = streamingWithToolsStrategy(),
            llmModel = OpenAIModels.Chat.GPT4o,
            toolRegistry = ToolRegistry { tools(SwitchTools(switch).asTools()) }
        ) {
            handleEvents {
                onLLMStreamingStarting { requests += it.prompt }
                onLLMStreamingFrameReceived { receivedFrames += it.streamFrame }
                onLLMStreamingCompleted { completedStreams++ }
                onAgentCompleted { finalPrompt = it.context.llm.readSession { prompt } }
            }
        }.use { agent ->
            assertEquals("The switch is on", agent.run("Turn on the switch"))
        }

        assertTrue(switch.isOn())
        assertEquals(3, requests.size)
        assertEquals(
            listOf(firstResponse.toMessageResponse()),
            requests[1].messages.filterIsInstance<Message.Assistant>()
        )
        assertEquals(
            listOf(firstResponse.toMessageResponse(), secondResponse.toMessageResponse()),
            requests[2].messages.filterIsInstance<Message.Assistant>()
        )
        assertToolResultFollowsCall(requests[1], "call-1", "switchState", "Switch is off")
        assertToolResultFollowsCall(requests[2], "call-2", "switch", "Switched to on")
        assertEquals(
            listOf(
                firstResponse.toMessageResponse(),
                secondResponse.toMessageResponse(),
                finalResponse.toMessageResponse()
            ),
            finalPrompt!!.messages.filterIsInstance<Message.Assistant>()
        )
        assertEquals(firstResponse + secondResponse + finalResponse, receivedFrames)
        assertEquals(3, completedStreams)
    }

    @Test
    fun testTextOnlyResponseIsSavedExactlyOnce() = runTest {
        val response = listOf(
            StreamFrame.TextDelta("Hello"),
            StreamFrame.TextComplete("Hello"),
            StreamFrame.End("stop")
        )
        val executor = getMockExecutor {
            mockLLMStream(response.asFlow()) onRequestEquals "Say hello"
        }
        var finalPrompt: Prompt? = null

        AIAgent(
            promptExecutor = executor,
            strategy = streamingWithToolsStrategy(),
            llmModel = OpenAIModels.Chat.GPT4o
        ) {
            handleEvents {
                onAgentCompleted { finalPrompt = it.context.llm.readSession { prompt } }
            }
        }.use { agent ->
            assertEquals("Hello", agent.run("Say hello"))
        }

        assertEquals(
            listOf(response.toMessageResponse()),
            finalPrompt!!.messages.filterIsInstance<Message.Assistant>()
        )
    }

    private fun assertToolResultFollowsCall(prompt: Prompt, id: String, name: String, output: String) {
        val callIndex = prompt.messages.indexOfFirst { message ->
            message.parts.filterIsInstance<MessagePart.Tool.Call>().any { it.id == id && it.tool == name }
        }
        assertTrue(callIndex >= 0, "The assistant tool call must be present")
        val resultMessage = prompt.messages[callIndex + 1]
        assertTrue(resultMessage is Message.User)
        val result = resultMessage.parts.filterIsInstance<MessagePart.Tool.Result>().single()
        assertEquals(id, result.id)
        assertEquals(name, result.tool)
        assertEquals(JsonPrimitive(output).toString(), result.output)
    }
}
