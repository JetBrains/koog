package ai.koog.agents.core.agent.session

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.environment.AIAgentEnvironment
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.DirectToolCallsEnabler
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
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

    private object TestToolsEnabler : DirectToolCallsEnabler

    private class TestEnvironment(private val toolRegistry: ToolRegistry) : AIAgentEnvironment {
        @OptIn(InternalAgentToolsApi::class)
        override suspend fun executeTools(toolCalls: List<Message.Tool.Call>): List<ReceivedToolResult> {
            return toolCalls.map { toolCall ->
                val tool = toolRegistry.getTool(toolCall.tool)
                val args = tool.decodeArgs(toolCall.contentJson)
                val result = tool.executeUnsafe(args, TestToolsEnabler)

                ReceivedToolResult(
                    id = toolCall.id,
                    tool = toolCall.tool,
                    content = tool.encodeResultToStringUnsafe(result),
                    result = result
                )
            }
        }

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
        }
    }

    class TestTool : SimpleTool<TestTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("Input parameter")
            val input: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "test-tool"
        override val description: String = "A test tool"

        override suspend fun doExecute(args: Args): String {
            return "Processed: ${args.input}"
        }
    }

    class CustomTool : Tool<CustomTool.Args, CustomTool.Result>() {
        @Serializable
        data class Args(val input: String)

        @Serializable
        data class Result(
            @property:LLMDescription("Input parameter")
            val output: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "custom-tool"
        override val description: String = "A custom tool"

        override val descriptor: ToolDescriptor = ToolDescriptor(
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
        model: LLModel = OllamaModels.Meta.LLAMA_3_2
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
    fun testUpdatePrompt() = runTest {
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Updated prompt response").asDefaultResponse
        }

        val initialPrompt = prompt("test", clock = testClock) {
            system("Initial system message")
            user("Initial user message")
        }

        val session = createSession(mockExecutor, prompt = initialPrompt)

        session.updatePrompt {
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
}
