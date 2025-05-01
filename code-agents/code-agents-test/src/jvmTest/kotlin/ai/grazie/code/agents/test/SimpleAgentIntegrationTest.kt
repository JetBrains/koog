package ai.grazie.code.agents.test

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.simpleApi.SayToUser
import ai.grazie.code.agents.local.simpleApi.simpleChatAgent
import ai.grazie.code.agents.local.simpleApi.simpleSingleRunAgent
import ai.jetbrains.code.prompt.llm.JetBrainsAIModels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.junit5.JUnit5Asserter

class SimpleAgentIntegrationTest {
    val systemPrompt = """
            You are a helpful assistant. 
            You MUST use tools to communicate to the user.
            You MUST NOT communicate to the user without tools.
        """.trimIndent()
    val apiToken = System.getenv("USER_STGN_JWT_TOKEN")

    private fun runBlockingWithToken(block: suspend CoroutineScope.(token: String) -> Unit) = runBlocking {
        if (apiToken.isNullOrBlank() || apiToken == "null") {
            return@runBlocking
        }
        block(apiToken)
    }
    

    @Test
    fun `simpleChatAgent should call default tools`() = runBlockingWithToken {
        val actualToolCalls = mutableListOf<String>()
        val eventHandler = EventHandler {
            onToolCall { stage, tool, args ->
                println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                actualToolCalls.add(tool.name)
            }

            handleError {
                JUnit5Asserter.fail("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            }

            handleResult {
                println("Agent result: $it")
            }
        }

        try {
            actualToolCalls.clear()

            val agent = simpleChatAgent(
                executor = null!!,
                cs = this,
                systemPrompt = systemPrompt,
                llmModel = JetBrainsAIModels.OpenAI.GPT4oMini,
                temperature = 1.0,
                eventHandler = eventHandler,
                maxIterations = 10,
            )

            agent.run("Please exit.")
            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
        } catch (e: Exception) {
            println("An error occurred: ${e.message}\n${e.stackTraceToString()}")
        }
    }

    @Test
    fun `simpleChatAgent should call a custom tool`() = runBlockingWithToken {
        val actualToolCalls = mutableListOf<String>()
        val eventHandler = EventHandler {
            onToolCall { stage, tool, args ->
                println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                actualToolCalls.add(tool.name)
            }

            handleError {
                JUnit5Asserter.fail("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            }

            handleResult {
                println("Agent result: $it")
            }
        }

        val toolRegistry = ToolRegistry {
            stage {
                tool(SayToUser)
            }
        }

        try {
            actualToolCalls.clear()

            val agent = simpleChatAgent(
                executor = null!!,
                cs = this,
                systemPrompt = systemPrompt,
                llmModel = JetBrainsAIModels.OpenAI.GPT4_1,
                temperature = 1.0,
                eventHandler = eventHandler,
                maxIterations = 10,
                toolRegistry = toolRegistry,
            )

            agent.run("Hello, how are you?")

            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
            assertTrue(actualToolCalls.contains("__say_to_user__"), "The __say_to_user__ tool was not called")
        } catch (e: Exception) {
            JUnit5Asserter.fail("An error occurred: ${e.message}\n${e.stackTraceToString()}")
        }
    }

    @Test
    fun `simpleSingleRunAgent should not call tools by default`() = runBlockingWithToken {
        val actualToolCalls = mutableListOf<String>()

        val eventHandler = EventHandler {
            onToolCall { stage, tool, args ->
                println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                actualToolCalls.add(tool.name)
            }

            handleError {
                JUnit5Asserter.fail("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            }

            handleResult {
                println("Agent result: $it")
            }
        }

        try {
            actualToolCalls.clear()

            val agent = simpleSingleRunAgent(
                executor = null!!,
                cs = this,
                systemPrompt = systemPrompt,
                llmModel = JetBrainsAIModels.OpenAI.GPT4oMini,
                temperature = 1.0,
                eventHandler = eventHandler,
                maxIterations = 10,
            )

            agent.run("Repeat what I say: hello, I'm good.")

            // by default, simpleSingleRunAgent has no tools underneath
            assertTrue(actualToolCalls.isEmpty(), "No tools should be called")
        } catch (e: Exception) {
            JUnit5Asserter.fail("An error occurred: ${e.message}\n${e.stackTraceToString()}")
        }
    }

    @Test
    fun `simpleSingleRunAgent should call a custom tool`() = runBlockingWithToken {
        val actualToolCalls = mutableListOf<String>()

        val eventHandler = EventHandler {
            onToolCall { stage, tool, args ->
                println("Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                actualToolCalls.add(tool.name)
            }

            handleError {
                JUnit5Asserter.fail("An error occurred: ${it.message}\n${it.stackTraceToString()}")
            }

            handleResult {
                println("Agent result: $it")
            }
        }

        val toolRegistry = ToolRegistry {
            stage {
                tool(SayToUser)
            }
        }

        try {
            actualToolCalls.clear()

            val agent = simpleSingleRunAgent(
                executor = null!!,
                cs = this,
                systemPrompt = systemPrompt,
                llmModel = JetBrainsAIModels.OpenAI.GPT4oMini,
                temperature = 1.0,
                eventHandler = eventHandler,
                toolRegistry = toolRegistry,
                maxIterations = 10,
            )

            agent.run("Write a Kotlin function to calculate factorial.")

            assertTrue(actualToolCalls.isNotEmpty(), "No tools were called")
            assertTrue(actualToolCalls.contains("__say_to_user__"), "The __say_to_user__ tool was not called")
        } catch (e: Exception) {
            JUnit5Asserter.fail("An error occurred: ${e.message}\n${e.stackTraceToString()}")
        }
    }
}
