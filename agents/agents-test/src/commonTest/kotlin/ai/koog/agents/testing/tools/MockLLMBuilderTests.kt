package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.streamFrameFlowOf
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("USELESS_CAST")
class MockLLMBuilderTests {
    private val serializer = KotlinxSerializer()

    // Sample tool for testing
    private object TestTool : Tool<TestTool.Args, String>(
        argsSerializer = serializer<Args>(),
        resultSerializer = serializer<String>(),
        name = "test_tool",
        description = "A test tool for testing"
    ) {
        @Serializable
        data class Args(val input: String)

        override suspend fun execute(args: Args): String =
            "Executed with: ${args.input}"
    }

    @Test
    fun testBasicMockLLMAnswer() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer("Hello, world!") onRequestContains "hello"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test") {
            user("Say hello to me")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Hello, world!", response.content)

        val prompt2 = prompt("test2") {
            user("Something unrelated")
        }

        val response2 = mockExecutor.execute(prompt2, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Default response", response2.content)
    }

    @Test
    fun testExactMatchResponse() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer("Exact match response") onRequestEquals "exact match query"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-exact") {
            user("exact match query")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Exact match response", response.content)

        val prompt2 = prompt("test-exact-partial") {
            user("This contains exact match query somewhere")
        }

        val response2 = mockExecutor.execute(prompt2, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Default response", response2.content)
    }

    @Test
    fun testPartialMatchResponse() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer("Partial match response") onRequestContains "partial match"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-partial") {
            user("This contains partial match somewhere")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Partial match response", response.content)
    }

    @Test
    fun testConditionalMatchResponse() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMAnswer("Conditional response") onCondition { it.length > 20 }
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-conditional") {
            user("This is a long message that should match the condition")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Conditional response", response.content)

        val prompt2 = prompt("test-conditional-short") {
            user("Short message")
        }

        val response2 = mockExecutor.execute(prompt2, OllamaModels.Meta.LLAMA_3_2).single()
        assertEquals("Default response", response2.content)
    }

    @Test
    fun testStreamMocking() = runTest {
        val prompt = prompt("test-stream") {
            user("hello")
        }
        val expectedStream = streamFrameFlowOf("hi", ", ho", "w are you?")
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMStream(expectedStream) onRequestEquals "hello"
        }

        val actualStream = mockExecutor.executeStreaming(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertContentEquals(expectedStream.toList(), actualStream.toList())
    }

    @Test
    fun testToolCallMocking() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockLLMToolCall(TestTool, TestTool.Args("test input")) onRequestContains "use tool"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-tool") {
            user("Please use tool to do something")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertTrue(response is Message.Tool.Call)
        val toolCall = response as Message.Tool.Call
        assertEquals("test_tool", toolCall.tool)
    }

    @Test
    fun testMultipleToolCallsMocking() = runTest {
        val toolCalls = listOf(
            TestTool to TestTool.Args("first input"),
            TestTool to TestTool.Args("second input")
        )

        val mockExecutor = getMockExecutor(serializer) {
            mockLLMToolCall(toolCalls) onRequestContains "use multiple tools"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-multiple-tools") {
            user("Please use multiple tools to do something")
        }

        val responses = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2, listOf())

        val responseToolCalls = responses.filterIsInstance<Message.Tool.Call>()
        assertEquals(2, responseToolCalls.size)
        assertEquals("test_tool", responseToolCalls[0].tool)
        assertEquals("test_tool", responseToolCalls[1].tool)
    }

    @Test
    fun testMixedResponseMocking() = runTest {
        val mixedToolCalls = listOf(
            TestTool to TestTool.Args("mixed input")
        )
        val textResponses = listOf("This is a mixed response with tool calls")

        val mockExecutor = getMockExecutor(serializer) {
            mockLLMMixedResponse(mixedToolCalls, textResponses) onRequestContains "mixed response"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-mixed") {
            user("I need a mixed response with tools")
        }

        val responses = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2, listOf())
        assertEquals(2, responses.size)
        assertTrue(responses.any { it is Message.Assistant })
        assertTrue(responses.any { it is Message.Tool.Call })

        val textResponse = responses.first { it is Message.Assistant } as Message.Assistant
        assertEquals("This is a mixed response with tool calls", textResponse.content)

        val toolCall = responses.first { it is Message.Tool.Call } as Message.Tool.Call
        assertEquals("test_tool", toolCall.tool)
    }

    @Test
    fun testToolBehaviorMocking() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockTool(TestTool) alwaysReturns "Mocked result"
            mockLLMToolCall(TestTool, TestTool.Args("test input")) onRequestContains "use tool"
        }

        val prompt = prompt("test-tool-behavior") {
            user("Please use tool to do something")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertTrue(response is Message.Tool.Call)

        val toolCall = response as Message.Tool.Call
        val toolCondition = (mockExecutor as MockPromptExecutor).toolActions.firstOrNull {
            it.tool.name == toolCall.tool
        }

        assertNotNull(toolCondition)

        val result = toolCondition.invoke(toolCall)
        assertTrue(result is String)
        assertEquals("Mocked result", result)
    }

    @Test
    fun testToolBehaviorWithCondition() = runTest {
        val mockExecutor = getMockExecutor(serializer) {
            mockTool(TestTool).returns("Specific result").onArguments(TestTool.Args("specific input"))
            mockTool(TestTool) alwaysReturns "Default result"
            mockLLMToolCall(TestTool, TestTool.Args("specific input")) onRequestContains "specific"
            mockLLMToolCall(TestTool, TestTool.Args("other input")) onRequestContains "other"
        }

        val specificPrompt = prompt("test-specific") {
            user("Please use tool with specific input")
        }

        val specificResponse = mockExecutor.execute(specificPrompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertTrue(specificResponse is Message.Tool.Call)

        val specificToolCall = specificResponse as Message.Tool.Call
        val specificToolCondition = (mockExecutor as MockPromptExecutor).toolActions.first {
            it.satisfies(specificToolCall)
        }

        val specificResult = specificToolCondition.invoke(specificToolCall)
        assertTrue(specificResult is String)
        assertEquals("Specific result", specificResult)

        val otherPrompt = prompt("test-other") {
            user("Please use tool with other input")
        }

        val otherResponse = mockExecutor.execute(otherPrompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertTrue(otherResponse is Message.Tool.Call)

        val otherToolCall = otherResponse as Message.Tool.Call
        val otherToolCondition = (mockExecutor as MockPromptExecutor).toolActions.first {
            it.satisfies(otherToolCall)
        }

        val otherResult = otherToolCondition.invoke(otherToolCall)
        assertTrue(otherResult is String)
        assertEquals("Default result", otherResult)
    }

    @Test
    fun testToolBehaviorWithCustomAction() = runTest {
        var actionCalled = false

        val mockExecutor = getMockExecutor(serializer) {
            mockTool(TestTool) alwaysDoes {
                actionCalled = true
                "Custom action result"
            }
            mockLLMToolCall(TestTool, TestTool.Args("test input")) onRequestContains "use tool"
        }

        val prompt = prompt("test-custom-action") {
            user("Please use tool to do something")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2).single()
        assertTrue(response is Message.Tool.Call)

        val toolCall = response
        val toolCondition = (mockExecutor as MockPromptExecutor).toolActions.first {
            it.satisfies(toolCall)
        }

        val result = toolCondition.invoke(toolCall)
        assertTrue(actionCalled)
        assertTrue(result is String)
        assertEquals("Custom action result", result)
    }
}
