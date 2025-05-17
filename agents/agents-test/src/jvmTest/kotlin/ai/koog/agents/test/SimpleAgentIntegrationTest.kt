package ai.koog.agents.test

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.simpleChatAgent
import ai.koog.agents.ext.agent.simpleSingleRunAgent
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.local.features.eventHandler.feature.EventHandler
import ai.koog.agents.local.features.eventHandler.feature.EventHandlerConfig
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.AfterTest
import kotlin.test.assertTrue

class SimpleAgentIntegrationTest {
    val systemPrompt = """
            You are a helpful assistant. 
            You MUST use tools to communicate to the user.
            You MUST NOT communicate to the user without tools.
        """.trimIndent()

    private fun readTestOpenAIKeyFromEnv(): String {
        return System.getenv("OPEN_AI_API_TEST_KEY") ?: error("ERROR: environment variable `OPEN_AI_API_TEST_KEY` not set")
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


    // ToDo add parametrisation for different LLMs
    @Test
    fun integration_simpleChatAgentShouldCallDefaultTools() = runBlocking {
        val agent = simpleChatAgent(
            executor = simpleOpenAIExecutor(readTestOpenAIKeyFromEnv()),
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Chat.GPT4o,
            temperature = 1.0,
            maxIterations = 10,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Please exit.")
        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
    }

    @Test
    fun integration_simpleChatAgentShouldCallCustomTools() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val agent = simpleChatAgent(
            executor = simpleOpenAIExecutor(readTestOpenAIKeyFromEnv()),
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            maxIterations = 10,
            toolRegistry = toolRegistry,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Hello, how are you?")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(actualToolCalls.contains("__say_to_user__"), "The __say_to_user__ tool was not called")
    }

    @Test
    fun integration_simpleSingleRunAgentShouldNotCallToolsByDefault() = runBlocking {
        val agent = simpleSingleRunAgent(
            executor = simpleOpenAIExecutor(readTestOpenAIKeyFromEnv()),
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            maxIterations = 10,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Repeat what I say: hello, I'm good.")

        // by default, simpleSingleRunAgent has no tools underneath
        assertTrue(actualToolCalls.isEmpty(), "No tools should be called")
    }

    @Test
    fun integration_simpleSingleRunAgentShouldCallCustomTool() = runBlocking {
        val toolRegistry = ToolRegistry {
            tool(SayToUser)
        }

        val agent = simpleSingleRunAgent(
            executor = simpleOpenAIExecutor(readTestOpenAIKeyFromEnv()),
            systemPrompt = systemPrompt,
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            temperature = 1.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            installFeatures = { install(EventHandler, eventHandlerConfig) }
        )

        agent.run("Write a Kotlin function to calculate factorial.")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        assertTrue(actualToolCalls.contains("__say_to_user__"), "The __say_to_user__ tool was not called")
    }
}
