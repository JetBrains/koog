package ai.koog.agents.test

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolException
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
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

    val testExecutor = getMockExecutor {
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
        onToolCallStarting { eventContext ->
            println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            actualToolCalls.add(eventContext.tool.name)
            iterationCount++
        }

        onAgentExecutionFailed { eventContext ->
            errors.add(eventContext.throwable)
        }

        onToolCallStarting { eventContext ->
            println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
            actualToolCalls.add(eventContext.tool.name)
        }

        onToolCallFailed { eventContext ->
            println(
                "Tool call failure: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}, error=${eventContext.throwable.message}"
            )
            errors.add(eventContext.throwable)
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

    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<Any?>()
    val llmRequestedTools = mutableListOf<String>()
    var iterationCount = 0

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
        errors.clear()
        results.clear()
        llmRequestedTools.clear()
        iterationCount = 0
    }

    object ErrorTool : SimpleTool<ErrorTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("Message for the error")
            val message: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "error_tool"
        override val description: String = "A tool that always throws an exception"

        override suspend fun doExecute(args: Args): String {
            throw ToolException.ValidationFailure("This tool always fails")
        }
    }

    object ConditionalTool : SimpleTool<ConditionalTool.Args>() {
        @Serializable
        data class Args(
            @property:LLMDescription("Condition that determines if the tool will succeed or fail")
            val condition: String
        )

        override val argsSerializer: KSerializer<Args> = Args.serializer()

        override val name: String = "conditional_tool"
        override val description: String = "A tool that conditionally throws an exception"

        override suspend fun doExecute(args: Args): String {
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
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Repeat after me: Hello, I'm good.")

        // by default, a AI Agent has no tools underneath
        assertTrue(actualToolCalls.isEmpty(), "No tools should be called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
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
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Write a Kotlin function to calculate factorial.")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(actualToolCalls.contains(SayToUser.name), "The ${SayToUser.name} tool was not called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
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
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        // Calling a non-existent tool returns an observation with an error
        // instead of throwing an exception, allowing the agent to handle it gracefully
        agent.run(errorTrigger)

        assertTrue(
            llmRequestedTools.contains(ErrorTool.name),
            "LLM should have requested ${ErrorTool.name}, but requested: $llmRequestedTools"
        )

        // Verify the tool was NOT actually executed (tool not found in registry)
        assertTrue(
            actualToolCalls.isEmpty(),
            "No tools should be executed when tool is not found, but found: $actualToolCalls"
        )

        // Verify agent completed successfully without exceptions
        assertTrue(results.isNotEmpty(), "Agent should complete and produce a result")

        // Verify no exceptions were thrown (graceful error handling)
        assertTrue(
            errors.isEmpty(),
            "No errors should be recorded with graceful error handling: ${errors.joinToString("\n") { it.message ?: "" }}"
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
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        try {
            agent.run(errorTrigger)
        } catch (e: Throwable) {
            errors.add(e)
        }

        assertTrue(actualToolCalls.contains(ErrorTool.name), "The ${ErrorTool.name} tool was not called")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
        )
    }

    @ParameterizedTest
    @MethodSource("getInputMessage")
    fun `test simpleSingleRunAgent handles conditional tool execution`(agentMessage: String) = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(ConditionalTool)
        }

        val successAgent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            promptExecutor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        successAgent.run(agentMessage)

        assertTrue(actualToolCalls.contains(ConditionalTool.name), "The ${ConditionalTool.name} tool was not called")
        assertTrue(errors.isEmpty(), "No errors should be recorded for success case")
    }

    @Test
    fun `test simpleSingleRunAgent fails after reaching maxIterations`() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val loopExecutor = getMockExecutor {
            mockLLMToolCall(SayToUser, SayToUser.Args("Looping...")) onRequestEquals "Make the agent loop."
        }

        iterationCount = 0

        val agent = AIAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 2,
            promptExecutor = loopExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        try {
            agent.run("Make the agent loop.")
        } catch (e: Throwable) {
            errors.add(e)
        }

        assertTrue(errors.isNotEmpty(), "Error should be recorded when maxIterations is reached")
        assertTrue(
            errors.any {
                it.message?.contains("Maximum number of iterations") == true ||
                    it.message?.contains("Agent couldn't finish in given number of steps") == true
            },
            "Expected error about maximum iterations"
        )
    }
}
