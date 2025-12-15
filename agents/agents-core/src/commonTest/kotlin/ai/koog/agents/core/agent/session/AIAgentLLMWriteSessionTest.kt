package ai.koog.agents.core.agent.session

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
class AIAgentLLMWriteSessionTest {
    private fun systemMessage(content: String) = Message.System(content, RequestMetaInfo.create(testClock))
    private fun userMessage(content: String) = Message.User(content, RequestMetaInfo.create(testClock))
    private fun assistantMessage(content: String) = Message.Assistant(content, ResponseMetaInfo.create(testClock))

    private class TestEnvironment(private val toolRegistry: ToolRegistry) : AIAgentEnvironment {

        @OptIn(InternalAgentToolsApi::class)
        override suspend fun executeTool(toolCall: Message.Tool.Call): ReceivedToolResult {
            val tool = toolRegistry.getTool(toolCall.tool)
            val args = tool.decodeArgs(toolCall.contentJson)
            val result = tool.executeUnsafe(args)

            return ReceivedToolResult(
                id = toolCall.id,
                tool = toolCall.tool,
                toolArgs = toolCall.contentJson,
                toolDescription = null,
                content = tool.encodeResultToStringUnsafe(result),
                resultKind = ToolResultKind.Success,
                result = tool.encodeResultUnsafe(result)
            )
        }

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
        }
    }

    class TestTool : SimpleTool<TestTool.Args>(
        argsSerializer = Args.serializer(),
        name = "test-tool",
        description = "A test tool"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("Input parameter")
            val input: String
        )

        override suspend fun execute(args: Args): String {
            return "Processed: ${args.input}"
        }
    }

    class CustomTool : Tool<CustomTool.Args, CustomTool.Result>(
        argsSerializer = Args.serializer(),
        resultSerializer = Result.serializer(),
        descriptor = ToolDescriptor(
            name = "custom-tool",
            description = "A custom tool",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "input",
                    description = "Input parameter",
                    type = ToolParameterType.String
                )
            )
        )
    ) {
        @Serializable
        data class Args(val input: String)

        @Serializable
        data class Result(
            @property:LLMDescription("Input parameter")
            val output: String
        )

        override suspend fun execute(args: Args): Result {
            return Result("Custom processed: ${args.input}")
        }

        override fun encodeResultToString(result: Result): String {
            return """{"output":"${result.output}"}"""
        }
    }

    private fun createConversationPrompt(id: String = "test-conversation"): Prompt {
        return prompt(id) {
            system("You are a helpful AI assistant that can use tools to accomplish tasks.")
            user("I need help analyzing some data.")
            assistant("I'd be happy to help you analyze your data. What kind of data are we working with?")
            user("I have some text that needs processing.")
            assistant("I'll use the test-tool to process your text.")
            tool {
                call("call_1", "test-tool", """{"input":"sample data"}""")
                result("call_1", "test-tool", "Processed: sample data")
            }
            assistant(
                "I've processed your sample data. The result was: Processed: sample data. Would you like me to do anything else with it?"
            )
            user("Can you also use the custom tool to process this data?")
            assistant("Sure, I'll use the custom tool for additional processing.")
            tool {
                call("call_2", "custom-tool", """{"input":"additional processing"}""")
                result("call_2", "custom-tool", """{"output":"Custom processed: additional processing"}""")
            }
            assistant(
                "I've completed the additional processing. The custom tool returned: Custom processed: additional processing"
            )
        }
    }

    private fun createSession(
        executor: PromptExecutor,
        tools: List<Tool<*, *>> = listOf(TestTool(), CustomTool()),
        prompt: Prompt = createConversationPrompt(),
        model: LLModel = OllamaModels.Meta.LLAMA_3_2,
        responseProcessor: ResponseProcessor? = null
    ): AIAgentLLMWriteSession {
        val toolRegistry = ToolRegistry {
            tools.forEach { tool(it) }
        }

        val toolDescriptors = tools.map { it.descriptor }
        val environment = TestEnvironment(toolRegistry)
        val config = AIAgentConfig(
            prompt = prompt,
            model = model,
            maxAgentIterations = 10
        )

        return AIAgentLLMWriteSession(
            environment = environment,
            executor = executor,
            tools = toolDescriptors,
            toolRegistry = toolRegistry,
            prompt = prompt,
            model = model,
            responseProcessor = responseProcessor,
            config = config,
            clock = testClock
        )
    }

    @Test
    fun testRequestLLM() = runTest {
        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("This is a test response").asDefaultResponse
        }

        val session = createSession(mockExecutor)
        val initialMessageCount = session.prompt.messages.size

        val response = session.requestLLM()

        assertEquals("This is a test response", response.content)
        assertEquals(initialMessageCount + 1, session.prompt.messages.size)
        assertEquals(assistantMessage("This is a test response"), session.prompt.messages.last())
    }

    @Test
    fun testRequestLLMWithoutTools() = runTest {
        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMAnswer("Response without tools").asDefaultResponse
        }

        val session = createSession(mockExecutor)
        val initialMessageCount = session.prompt.messages.size

        val response = session.requestLLMWithoutTools()

        assertEquals("Response without tools", response.content)
        assertEquals(initialMessageCount + 1, session.prompt.messages.size)
        assertEquals(assistantMessage("Response without tools"), session.prompt.messages.last())
    }

    @Test
    fun testCallTool() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Tool response").asDefaultResponse
        }

        val testTool = TestTool()
        val session = createSession(mockExecutor, listOf(testTool))

        val result = session.callTool(testTool, TestTool.Args("test input"))

        assertTrue(result.isSuccessful())
        assertEquals("Processed: test input", result.asSuccessful().result)
    }

    @Test
    fun testCallToolByName() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Tool response").asDefaultResponse
        }

        val testTool = TestTool()
        val session = createSession(mockExecutor, listOf(testTool))

        val result = session.callTool("test-tool", TestTool.Args("test input"))

        assertTrue(result.isSuccessful())
        assertEquals("Processed: test input", result.asSuccessful().result)
    }

    @Test
    fun testCallToolRaw() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Tool response").asDefaultResponse
        }

        val testTool = TestTool()
        val session = createSession(mockExecutor, listOf(testTool))

        val result = session.callToolRaw("test-tool", TestTool.Args("test input"))

        assertEquals("Processed: test input", result)
    }

    @Test
    fun testFindTool() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Tool response").asDefaultResponse
        }

        val testTool = TestTool()
        val session = createSession(mockExecutor, listOf(testTool))

        val safeTool = session.findTool<TestTool.Args, String>(TestTool::class)
        assertNotNull(safeTool)

        val result = safeTool.execute(TestTool.Args("test input"))
        assertTrue(result.isSuccessful())
        assertEquals("Processed: test input", result.asSuccessful().result)
    }

    @Test
    fun testCustomTool() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Custom tool response").asDefaultResponse
        }

        val customTool = CustomTool()
        val session = createSession(mockExecutor, listOf(customTool))

        val result = session.callTool(customTool, CustomTool.Args("test input"))

        assertTrue(result.isSuccessful())
        assertEquals("Custom processed: test input", result.asSuccessful().result.output)
    }

    @Test
    fun testAppendPrompt() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Updated prompt response").asDefaultResponse
        }

        val initialPrompt = prompt("test", clock = testClock) {
            system("Initial system message")
            user("Initial user message")
        }

        val session = createSession(mockExecutor, prompt = initialPrompt)

        session.appendPrompt {
            user("Additional user message")
        }

        assertEquals(3, session.prompt.messages.size)
        assertEquals(systemMessage("Initial system message"), session.prompt.messages[0])
        assertEquals(userMessage("Initial user message"), session.prompt.messages[1])
        assertEquals(userMessage("Additional user message"), session.prompt.messages[2])

        val response = session.requestLLM()
        assertEquals("Updated prompt response", response.content)
    }

    @Test
    fun testRewritePrompt() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Rewritten prompt response").asDefaultResponse
        }

        val initialPrompt = prompt("test", clock = testClock) {
            system("Initial system message")
            user("Initial user message")
        }

        val session = createSession(mockExecutor, prompt = initialPrompt)

        session.rewritePrompt { oldPrompt ->
            prompt("rewritten", clock = testClock) {
                system("Rewritten system message")
                user("Rewritten user message")
            }
        }

        assertEquals(2, session.prompt.messages.size)
        assertEquals(systemMessage("Rewritten system message"), session.prompt.messages[0])
        assertEquals(userMessage("Rewritten user message"), session.prompt.messages[1])

        val response = session.requestLLM()
        assertEquals("Rewritten prompt response", response.content)
    }

    @Test
    fun testChangeModel() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Changed model response").asDefaultResponse
        }

        val initialModel = OllamaModels.Meta.LLAMA_3_2
        val newModel = OllamaModels.Meta.LLAMA_4

        val session = createSession(mockExecutor, model = initialModel)
        assertEquals(initialModel, session.model)

        session.changeModel(newModel)
        assertEquals(newModel, session.model)

        val response = session.requestLLM()
        assertEquals("Changed model response", response.content)
    }

    @Test
    fun testChangeLLMParams() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Changed params response").asDefaultResponse
        }

        val session = createSession(mockExecutor)

        session.changeLLMParams(LLMParams(temperature = 0.5))
        assertEquals(0.5, session.prompt.params.temperature)

        val response = session.requestLLM()
        assertEquals("Changed params response", response.content)
    }

    @Test
    fun testRequestLLMMultipleOnlyCallingTools() = runTest {
        val thinkingContent = "<thinking>I need to use a tool</thinking>"
        val testTool = TestTool()

        val mockExecutor = getMockExecutor(clock = testClock) {
            // Simulate [Assistant, ToolCall] sequence
            mockLLMMixedResponse(
                toolCalls = listOf(testTool to TestTool.Args("test")),
                responses = listOf(thinkingContent)
            ) onCondition { true }
        }

        val session = createSession(mockExecutor, listOf(testTool))

        val responses = session.requestLLMMultipleOnlyCallingTools()

        assertEquals(2, responses.size)
        assertEquals(thinkingContent, (responses[0] as Message.Assistant).content)
        assertEquals("test-tool", (responses[1] as Message.Tool.Call).tool)

        // Verify that BOTH messages were appended to the prompt history in correct order
        val lastTwoMessages = session.prompt.messages.takeLast(2)
        assertEquals(thinkingContent, (lastTwoMessages[0] as Message.Assistant).content)
        assertEquals("test-tool", (lastTwoMessages[1] as Message.Tool.Call).tool)
    }

    @Test
    fun testRequestLLMOnlyCallingToolsWithThinking() = runTest {
        val thinkingContent = "<thinking>Checking file...</thinking>"
        val testTool = TestTool()

        val mockExecutor = getMockExecutor(clock = testClock) {
            mockLLMMixedResponse(
                toolCalls = listOf(testTool to TestTool.Args("test")),
                responses = listOf(thinkingContent)
            ) onCondition { true }
        }

        val session = createSession(mockExecutor, listOf(testTool))

        val response = session.requestLLMOnlyCallingTools()

        // It should strictly return the ToolCall (fixing the bug), skipping the thinking message
        assertTrue(response is Message.Tool.Call, "Expected response to be a Tool Call, not the thinking message")
        assertEquals("test-tool", response.tool)

        // It should still persist the "Thinking" message in history in correct order
        val lastTwoMessages = session.prompt.messages.takeLast(2)
        assertEquals(thinkingContent, (lastTwoMessages[0] as Message.Assistant).content)
        assertEquals("test-tool", (lastTwoMessages[1] as Message.Tool.Call).tool)
    }

    @Test
    fun testRequestLLMOnlyCallingToolsNoToolCallThrowsException() = runTest {
        val mockExecutor = getMockExecutor(clock = testClock) {
            // Simulate model refusing to use tools and just responding with text
            mockLLMAnswer("I cannot use tools for this request.").asDefaultResponse
        }

        val session = createSession(mockExecutor, listOf(TestTool()))

        val exception = kotlin.runCatching {
            session.requestLLMOnlyCallingTools()
        }.exceptionOrNull()

        assertNotNull(exception, "Expected an exception when no tool call is found")
        assertTrue(
            exception is IllegalStateException,
            "Expected IllegalStateException but got ${exception::class.simpleName}"
        )
        assertTrue(
            exception.message?.contains("expected at least one Tool.Call") == true,
            "Exception message should indicate missing tool call"
        )
    }

    @Test
    fun testRequestLLMOnlyCallingToolsWithMultipleToolCalls() = runTest {
        val testTool = TestTool()

        val mockExecutor = getMockExecutor(clock = testClock) {
            // Simulate model returning multiple tool calls (parallel tool calling)
            mockLLMMixedResponse(
                toolCalls = listOf(
                    testTool to TestTool.Args("first"),
                    testTool to TestTool.Args("second")
                ),
                responses = emptyList()
            ) onCondition { true }
        }

        val session = createSession(mockExecutor, listOf(testTool))

        val response = session.requestLLMOnlyCallingTools()

        // Should return the first tool call
        assertTrue(response is Message.Tool.Call, "Expected response to be a Tool Call")
        assertEquals("test-tool", response.tool)

        // Both tool calls should be in history
        val lastTwoMessages = session.prompt.messages.takeLast(2)
        assertTrue(lastTwoMessages.all { it is Message.Tool.Call })
    }
}
