package ai.koog.integration.tests.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.integration.tests.InjectOllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixture
import ai.koog.integration.tests.OllamaTestFixtureExtension
import ai.koog.integration.tests.utils.annotations.Retry
import ai.koog.integration.tests.utils.annotations.RetryExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@ExtendWith(OllamaTestFixtureExtension::class)
@ExtendWith(RetryExtension::class)
class OllamaSimpleAgentIntegrationTest {
    companion object {
        @field:InjectOllamaTestFixture
        private lateinit var fixture: OllamaTestFixture
        private val ollamaSimpleExecutor get() = fixture.executor
        private val ollamaModel get() = fixture.model
    }

    val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
        onToolCallStarting { eventContext ->
            actualToolCalls.add(eventContext.tool.name)
        }
    }

    val actualToolCalls = mutableListOf<String>()

    @AfterTest
    fun teardown() {
        actualToolCalls.clear()
    }

    @Retry
    @Test
    fun ollama_simpleTest() = runTest(timeout = 600.seconds) {
        val toolRegistry = ToolRegistry.Companion {
            tool(SayToUser)
        }

        val bookwormPrompt = """
            You're top librarian, helping user to find books.
            ALWAYS communicate to user via tools!!!
            ALWAYS use tools you've been provided.
            ALWAYS generate valid JSON responses.
            ALWAYS call tool correctly, with valid arguments.
            NEVER provide tool call in result body.
            
            Example tool call:
            {
                "id":"ollama_tool_call_3743609160",
                "tool":"say_to_user",
                "content":{"message":"The top 10 books of all time are:\n 1. Don Quixote by Miguel de Cervantes\n 2. A Tale of Two Cities by Charles Dickens\n 3. The Lord of the Rings by J.R.R. Tolkien\n 4. Pride and Prejudice by Jane Austen\n 5. To Kill a Mockingbird by Harper Lee\n 6. The Catcher in the Rye by J.D. Salinger\n 7. 1984 by George Orwell\n 8. The Great Gatsby by F. Scott Fitzgerald\n 9. War and Peace by Leo Tolstoy\n 10. Alice’s Adventures in Wonderland by Lewis Carroll"})
            }
        """.trimIndent()

        val agent = AIAgent(
            promptExecutor = ollamaSimpleExecutor,
            systemPrompt = bookwormPrompt,
            llmModel = ollamaModel,
            temperature = 0.0,
            toolRegistry = toolRegistry,
            maxIterations = 10,
            installFeatures = { install(EventHandler.Feature, eventHandlerConfig) }
        )

        agent.run("Give me top 10 books of the all time.")

        assertTrue(actualToolCalls.isNotEmpty(), "No tools were called for model")
        assertTrue(
            actualToolCalls.contains(SayToUser.name),
            "The ${SayToUser.name} tool was not called for model"
        )
    }
}
