package ai.koog.agents.test

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.toAgentError
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleAgentMockedTest {
    companion object {
        @JvmStatic
        fun getInputMessage(): Array<String> = arrayOf(
            "Call conditional tool with success.",
            "Call conditional tool with error.",
        )

        @JvmStatic
        fun getToolRegistry(): Array<ToolRegistry> = arrayOf(
            ToolRegistry { },
            ToolRegistry { tool(SayToUser) }
        )
    }

    val errorTrigger = "Trigger an error."

    val systemPrompt = """
            You are a helpful assistant. 
            You MUST use tools to communicate to the user.
            You MUST NOT communicate to the user without tools.
    """.trimIndent()

    val succeedToolCalls = mutableListOf<String>()
    val failedToolCalls = mutableListOf<String>()
    val errors = mutableListOf<AIAgentError>()
    val results = mutableListOf<Any?>()
    val llmRequestedTools = mutableListOf<String>()

    val testExecutor = getMockExecutor(
        toolRegistry = ToolRegistry {
            tool(ErrorTool)
            tool(ConditionalTool)
            tool(SayToUser)
            tool(ExitTool)
        }
    ) {
        mockLLMToolCall(ExitTool, ExitTool.Args("Bye-bye.")) onRequestEquals "Please exit."
        mockLLMToolCall(SayToUser, SayToUser.Args("Fine, and you?")) onRequestEquals "Hello, how are you?"
        mockLLMAnswer("Hello, I'm good.") onRequestEquals "Repeat after me: Hello, I'm good."
        mockLLMToolCall(
            SayToUser,
            SayToUser.Args("Calculating...")
        ) onRequestEquals "Write a Kotlin function to calculate factorial."
        mockLLMToolCall(
            ErrorTool,
            ErrorTool.Args("test")
        ) onRequestEquals errorTrigger
        mockLLMToolCall(
            ConditionalTool,
            ConditionalTool.Args("success")
        ) onRequestEquals "Call conditional tool with success."
        mockLLMToolCall(
            ConditionalTool,
            ConditionalTool.Args("error")
        ) onRequestEquals "Call conditional tool with error."
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCallCompleted { eventContext ->
            succeedToolCalls.add(eventContext.toolName)
        }

        onAgentExecutionFailed { eventContext ->
            eventContext.throwable.let { errors.add(it.toAgentError()) }
        }

        onToolCallFailed { eventContext ->
            failedToolCalls.add(eventContext.toolName)
            eventContext.error?.let { errors.add(it) }
        }

        onAgentCompleted { eventContext ->
            results.add(eventContext.result)
        }

        onLLMCallCompleted { eventContext ->
            // Capture which tools the LLM requested (whether they exist or not)
            eventContext.responses.filterIsInstance<Message.Tool.Call>().forEach { toolCall ->
                llmRequestedTools.add(toolCall.tool)
            }
        }
    }

    @AfterTest
    fun teardown() {
        succeedToolCalls.clear()
        failedToolCalls.clear()
        errors.clear()
        results.clear()
        llmRequestedTools.clear()
    }

    object ErrorTool : SimpleTool<ErrorTool.Args>(
        argsSerializer = Args.serializer(),
        name = "error_tool",
        description = "A tool that always throws an exception"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("Message for the error")
            val message: String
        )

        override suspend fun execute(args: Args): String {
            throw ToolException.ValidationFailure("This tool always fails")
        }
    }

    object ConditionalTool : SimpleTool<ConditionalTool.Args>(
        argsSerializer = Args.serializer(),
        name = "conditional_tool",
        description = "A tool that conditionally throws an exception"
    ) {
        @Serializable
        data class Args(
            @property:LLMDescription("Condition that determines if the tool will succeed or fail")
            val condition: String
        )

        override suspend fun execute(args: Args): String {
            if (args.condition == "error") {
                throw ToolException.ValidationFailure("Conditional failure triggered")
            }
            return "Conditional success"
        }
    }

    @Test
    fun ` test AIAgent doesn't call tools by default`() = runBlocking {
        val agent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            maxIterations = 10,
            promptExecutor = testExecutor,
            installFeatures = {
                withTesting()
                install(EventHandler, eventHandlerConfig)
            }
        )

        agent.run("Repeat after me: Hello, I'm good.")

        // by default, an AI Agent has no tools underneath
        assertTrue(succeedToolCalls.isEmpty(), "No tools should be called")
        assertTrue(failedToolCalls.isEmpty(), "No tools should be called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message }}"
        )
    }

    @Test
    fun `test AIAgent calls a custom tool`() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val agent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            promptExecutor = testExecutor,
            installFeatures = {
                withTesting()
                install(EventHandler, eventHandlerConfig)
            }
        )

        agent.run("Write a Kotlin function to calculate factorial.", null)

        assertTrue(succeedToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(succeedToolCalls.contains(SayToUser.name), "The ${SayToUser.name} tool was not called")
        assertTrue(failedToolCalls.isEmpty(), "Some tool calls weren't successful")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message }}"
        )
    }

    @ParameterizedTest
    @MethodSource("getToolRegistry")
    fun `test simpleSingleRunAgent handles non-registered tools`(toolRegistry: ToolRegistry) = runBlocking {
        val agent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            promptExecutor = testExecutor,
            installFeatures = {
                withTesting()
                install(EventHandler, eventHandlerConfig)
            }
        )

        // Calling a non-existent tool returns an observation with an error
        // instead of throwing an exception, allowing the agent to handle it gracefully
        try {
            agent.run(errorTrigger, null)
        } catch (e: Throwable) {
            errors.add(e.toAgentError())
        }

        assertTrue(
            llmRequestedTools.contains(ErrorTool.name),
            "LLM should have requested ${ErrorTool.name}, but requested: $llmRequestedTools"
        )

        assertTrue(
            succeedToolCalls.isEmpty() && failedToolCalls.isEmpty(),
            "No tools should be executed when tool is not found"
        )

        // Verify that tool not found errors are captured
        assertTrue(
            errors.isNotEmpty(),
            "Tool not found errors should be captured: ${errors.joinToString("\n") { it.message }}"
        )
    }

    @Test
    fun `test simpleSingleRunAgent handles tool execution errors`() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(ErrorTool)
        }

        val agent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            promptExecutor = testExecutor,
            installFeatures = {
                withTesting()
                install(EventHandler, eventHandlerConfig)
            }
        )

        try {
            agent.run(errorTrigger, null)
        } catch (e: Throwable) {
            errors.add(e.toAgentError())
        }

        assertTrue(llmRequestedTools.contains(ErrorTool.name), "The ${ErrorTool.name} tool was not requested by LLM")
        assertTrue(
            succeedToolCalls.isEmpty() && failedToolCalls.isEmpty(),
            "No tools should be executed when error is thrown"
        )
        assertTrue(
            errors.isNotEmpty(),
            "Expected tool execution errors to be captured"
        )
    }

    @ParameterizedTest
    @MethodSource("getInputMessage")
    fun `test simpleSingleRunAgent handles conditional tool execution`(agentMessage: String) = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(ConditionalTool)
        }

        try {
            AIAgent(
                systemPrompt = systemPrompt,
                llmModel = OpenAIModels.Chat.GPT4oMini,
                temperature = 1.0,
                toolRegistry = toolRegistry,
                maxIterations = 10,
                promptExecutor = testExecutor,
                installFeatures = {
                    withTesting()
                    install(EventHandler, eventHandlerConfig)
                }
            ).run(agentMessage)
        } catch (e: Throwable) {
            errors.add(e.toAgentError())
        }

        assertTrue(
            llmRequestedTools.contains(ConditionalTool.name),
            "LLM should have requested ${ConditionalTool.name}"
        )

        if (agentMessage.contains("success")) {
            assertTrue(succeedToolCalls.contains(ConditionalTool.name), "Success case should track tool execution")
            assertTrue(errors.isEmpty(), "Success case should have no errors")
            assertTrue(results.isNotEmpty(), "Agent should complete and produce a result for success case")
        } else {
            assertTrue(errors.isNotEmpty(), "Error case should capture errors")
        }
    }

    @Test
    fun `test simpleSingleRunAgent fails after reaching maxIterations`() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val loopExecutor = getMockExecutor {
            mockLLMToolCall(SayToUser, SayToUser.Args("Looping...")) onRequestEquals "Make the agent loop."
        }

        val agent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 2,
            promptExecutor = loopExecutor,
            installFeatures = {
                withTesting()
                install(EventHandler, eventHandlerConfig)
            }
        )

        try {
            agent.run("Make the agent loop.", null)
        } catch (e: Throwable) {
            errors.add(e.toAgentError())
        }

        assertTrue(errors.isNotEmpty(), "Error should be recorded when maxIterations is reached")
        assertTrue(
            errors.any {
                it.message.contains("Maximum number of iterations") || it.message.contains("Agent couldn't finish in given number of steps")
            },
            "Expected error about maximum iterations"
        )
    }
}
