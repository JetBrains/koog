package ai.grazie.code.agents.test

import ai.grazie.code.agents.core.api.simpleChatAgent
import ai.grazie.code.agents.core.api.simpleSingleRunAgent
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.tools.tools.SayToUser
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandler
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandlerConfig
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.CoroutineScope
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
    private val apiToken = System.getenv("USER_STGN_JWT_TOKEN")

    private fun runBlockingWithToken(block: suspend CoroutineScope.(token: String) -> Unit) = runBlocking {
        if (apiToken.isNullOrBlank() || apiToken == "null") {
            return@runBlocking
        }
        block(apiToken)
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCall = { stage, tool, args ->
            println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
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
    fun integration_simpleChatAgentShouldCallDefaultTools() = runBlockingWithToken {
        val agent = simpleChatAgent(
            executor = simpleOpenAIExecutor(apiToken),
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
    fun integration_simpleChatAgentShouldCallCustomTools() = runBlockingWithToken {
        val toolRegistry = ToolRegistry {
            stage {
                tool(SayToUser)
            }
        }

        val agent = simpleChatAgent(
            executor = simpleOpenAIExecutor(apiToken),
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
    fun integration_simpleSingleRunAgentShouldNotCallToolsByDefault() = runBlockingWithToken {
        val agent = simpleSingleRunAgent(
            executor = simpleOpenAIExecutor(apiToken),
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
    fun integration_simpleSingleRunAgentShouldCallCustomTool() = runBlockingWithToken {
        val toolRegistry = ToolRegistry {
            stage {
                tool(SayToUser)
            }
        }

        val agent = simpleSingleRunAgent(
            executor = simpleOpenAIExecutor(apiToken),
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
