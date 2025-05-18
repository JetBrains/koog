package ai.koog.agents.test

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.simpleChatAgent
import ai.koog.agents.ext.agent.simpleSingleRunAgent
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.local.features.eventHandler.feature.EventHandler
import ai.koog.agents.local.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class SimpleAgentMockedTest {
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
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCall = { tool, args ->
            println("Tool called: tool ${tool.name}, args $args")
            actualToolCalls.add(tool.name)
        }

        onAgentRunError = { strategyName, throwable ->
            errors.add(throwable)
        }

        onAgentFinished = { strategyName, result ->
            results.add(result)
        }
    }

    val actualToolCalls = mutableListOf<String>()
    val errors = mutableListOf<Throwable>()
    val results = mutableListOf<String?>()

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
        errors.clear()
        results.clear()
    }

    @Test
    fun `simpleChatAgent should call default tools`() = runBlocking {
        val agent = simpleChatAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            maxIterations = 10,
            executor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Please exit.")
        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
        )
    }

    @Test
    fun `simpleChatAgent should call a custom tool`() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val agent = simpleChatAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            maxIterations = 10,
            toolRegistry = toolRegistry,
            executor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Hello, how are you?")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(actualToolCalls.contains("__say_to_user__"), "The __say_to_user__ tool was not called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
        )
    }

    @Test
    fun `simpleSingleRunAgent should not call tools by default`() = runBlocking {
        val agent = simpleSingleRunAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            maxIterations = 10,
            executor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Repeat after me: Hello, I'm good.")

        // by default, a simpleSingleRunAgent has no tools underneath
        assertTrue(actualToolCalls.isEmpty(), "No tools should be called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
        )
    }

    @Test
    fun `simpleSingleRunAgent should call a custom tool`() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val agent = simpleSingleRunAgent(
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            executor = testExecutor,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Write a Kotlin function to calculate factorial.")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(actualToolCalls.contains("__say_to_user__"), "The __say_to_user__ tool was not called")
        assertTrue(results.isNotEmpty(), "No agent run results were received")
        assertTrue(
            errors.isEmpty(),
            "Expected no errors, but got: ${errors.joinToString("\n") { it.message ?: "" }}"
        )
    }
}