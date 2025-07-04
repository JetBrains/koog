package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.ToolResult
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("USELESS_CAST")
class MockLLMBuilderTests {

    // Sample tool for testing
    private object TestTool : Tool<TestTool.Args, ToolResult.Text>() {
        @Serializable
        data class Args(val input: String) : ToolArgs

        override val argsSerializer: KSerializer<Args> = serializer()

        override val descriptor: ToolDescriptor = ToolDescriptor(
            name = "test_tool",
            description = "A test tool for testing"
        )

        override suspend fun execute(args: Args): ToolResult.Text {
            return ToolResult.Text("Executed with: ${args.input}")
        }
    }

    @Test
    fun testBasicMockLLMAnswer() = runTest {
        // Create a mock executor with a simple response
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Hello, world!") onRequestContains "hello"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        // Test that the executor returns the expected response
        val prompt = prompt("test") {
            user("Say hello to me")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Hello, world!", response.content)

        // Test default response
        val prompt2 = prompt("test2") {
            user("Something unrelated")
        }

        val response2 = mockExecutor.execute(prompt2, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Default response", response2.content)
    }

    @Test
    fun testExactMatchResponse() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Exact match response") onRequestEquals "exact match query"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-exact") {
            user("exact match query")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Exact match response", response.content)

        // Test that partial match doesn't work for exact matching
        val prompt2 = prompt("test-exact-partial") {
            user("This contains exact match query somewhere")
        }

        val response2 = mockExecutor.execute(prompt2, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Default response", response2.content)
    }

    @Test
    fun testPartialMatchResponse() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Partial match response") onRequestContains "partial match"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        // Test that partial match works
        val prompt = prompt("test-partial") {
            user("This contains partial match somewhere")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Partial match response", response.content)
    }

    @Test
    fun testConditionalMatchResponse() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Conditional response") onCondition { it.length > 20 }
            mockLLMAnswer("Default response").asDefaultResponse
        }

        // Test that conditional match works
        val prompt = prompt("test-conditional") {
            user("This is a long message that should match the condition")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Conditional response", response.content)

        // Test that condition not matching returns default
        val prompt2 = prompt("test-conditional-short") {
            user("Short message")
        }

        val response2 = mockExecutor.execute(prompt2, OllamaModels.Meta.LLAMA_3_2)
        assertEquals("Default response", response2.content)
    }

    @Test
    fun testToolCallMocking() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val mockExecutor = getMockExecutor(toolRegistry) {
            mockLLMToolCall(TestTool, TestTool.Args("test input")) onRequestContains "use tool"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-tool") {
            user("Please use tool to do something")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertTrue(response is Message.Tool.Call)
        val toolCall = response as Message.Tool.Call
        assertEquals("test_tool", toolCall.tool)
    }

    @Test
    fun testMultipleToolCallsMocking() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val toolCalls = listOf(
            TestTool to TestTool.Args("first input"),
            TestTool to TestTool.Args("second input")
        )

        val mockExecutor = getMockExecutor(toolRegistry) {
            mockLLMToolCall(toolCalls) onRequestContains "use multiple tools"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-multiple-tools") {
            user("Please use multiple tools to do something")
        }

        // In this case, we expect a list of responses with tool calls
        val responses = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2, listOf())

        // Verify we have the expected number of tool calls
        val responseToolCalls = responses.filterIsInstance<Message.Tool.Call>()
        assertEquals(2, responseToolCalls.size)
        assertEquals("test_tool", responseToolCalls[0].tool)
        assertEquals("test_tool", responseToolCalls[1].tool)
    }

    @Test
    fun testMixedResponseMocking() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val mixedToolCalls = listOf(
            TestTool to TestTool.Args("mixed input")
        )

        val textResponses = listOf("This is a mixed response with tool calls")

        val mockExecutor = getMockExecutor(toolRegistry) {
            mockLLMMixedResponse(mixedToolCalls, textResponses) onRequestContains "mixed response"
            mockLLMAnswer("Default response").asDefaultResponse
        }

        val prompt = prompt("test-mixed") {
            user("I need a mixed response with tools")
        }

        val responses = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2, listOf())
        assertEquals(2, responses.size)

        // Check that we have both text and tool responses
        assertTrue(responses.any { it is Message.Assistant })
        assertTrue(responses.any { it is Message.Tool.Call })

        val textResponse = responses.first { it is Message.Assistant } as Message.Assistant
        assertEquals("This is a mixed response with tool calls", textResponse.content)

        val toolCall = responses.first { it is Message.Tool.Call } as Message.Tool.Call
        assertEquals("test_tool", toolCall.tool)
    }

    @Test
    fun testToolBehaviorMocking() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val mockExecutor = getMockExecutor(toolRegistry) {
            // Mock the tool behavior
            mockTool(TestTool) alwaysReturns ToolResult.Text("Mocked result")

            // Set up a tool call that will use the mocked tool
            mockLLMToolCall(TestTool, TestTool.Args("test input")) onRequestContains "use tool"
        }

        val prompt = prompt("test-tool-behavior") {
            user("Please use tool to do something")
        }

        // Execute the prompt to get a tool call
        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertTrue(response is Message.Tool.Call)

        // Now simulate executing the tool
        val toolCall = response as Message.Tool.Call

        // Find the tool condition that matches this call
        val toolCondition = (mockExecutor as MockLLMExecutor).toolActions.firstOrNull { 
            it.tool.name == toolCall.tool 
        }

        assertNotNull(toolCondition)

        // Execute the tool and check the result
        val result = toolCondition.invoke(toolCall)
        assertTrue(result is ToolResult.Text)
        assertEquals("Mocked result", (result as ToolResult.Text).text)
    }

    @Test
    fun testToolBehaviorWithCondition() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        val mockExecutor = getMockExecutor(toolRegistry) {
            // Mock the tool behavior with a condition
            mockTool(TestTool).returns(ToolResult.Text("Specific result")).onArguments(TestTool.Args("specific input"))
            mockTool(TestTool) alwaysReturns ToolResult.Text("Default result")

            // Set up tool calls
            mockLLMToolCall(TestTool, TestTool.Args("specific input")) onRequestContains "specific"
            mockLLMToolCall(TestTool, TestTool.Args("other input")) onRequestContains "other"
        }

        // Test the specific input
        val specificPrompt = prompt("test-specific") {
            user("Please use tool with specific input")
        }

        val specificResponse = mockExecutor.execute(specificPrompt, OllamaModels.Meta.LLAMA_3_2)
        assertTrue(specificResponse is Message.Tool.Call)

        val specificToolCall = specificResponse as Message.Tool.Call
        val specificToolCondition = (mockExecutor as MockLLMExecutor).toolActions.first { 
            it.satisfies(specificToolCall)
        }

        val specificResult = specificToolCondition.invoke(specificToolCall)
        assertTrue(specificResult is ToolResult.Text)
        assertEquals("Specific result", (specificResult as ToolResult.Text).text)

        // Test the default behavior
        val otherPrompt = prompt("test-other") {
            user("Please use tool with other input")
        }

        val otherResponse = mockExecutor.execute(otherPrompt, OllamaModels.Meta.LLAMA_3_2)
        assertTrue(otherResponse is Message.Tool.Call)

        val otherToolCall = otherResponse as Message.Tool.Call
        val otherToolCondition = (mockExecutor as MockLLMExecutor).toolActions.first { 
            it.satisfies(otherToolCall)
        }

        val otherResult = otherToolCondition.invoke(otherToolCall)
        assertTrue(otherResult is ToolResult.Text)
        assertEquals("Default result", (otherResult as ToolResult.Text).text)
    }

    @Test
    fun testToolBehaviorWithCustomAction() = runTest {
        val toolRegistry = ToolRegistry {
            tool(TestTool)
        }

        var actionCalled = false

        val mockExecutor = getMockExecutor(toolRegistry) {
            // Mock the tool behavior with a custom action
            mockTool(TestTool) alwaysDoes {
                actionCalled = true
                ToolResult.Text("Custom action result")
            }

            // Set up a tool call
            mockLLMToolCall(TestTool, TestTool.Args("test input")) onRequestContains "use tool"
        }

        val prompt = prompt("test-custom-action") {
            user("Please use tool to do something")
        }

        val response = mockExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2)
        assertTrue(response is Message.Tool.Call)

        val toolCall = response
        val toolCondition = (mockExecutor as MockLLMExecutor).toolActions.first { 
            it.satisfies(toolCall)
        }

        val result = toolCondition.invoke(toolCall)
        assertTrue(actionCalled)
        assertTrue(result is ToolResult.Text)
        assertEquals("Custom action result", (result as ToolResult.Text).text)
    }
}
