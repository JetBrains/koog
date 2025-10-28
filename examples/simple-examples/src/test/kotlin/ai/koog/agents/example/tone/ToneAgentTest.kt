package ai.koog.agents.example.tone

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.tone.ToneTools.NegativeToneTool
import ai.koog.agents.example.tone.ToneTools.NeutralToneTool
import ai.koog.agents.example.tone.ToneTools.PositiveToneTool
import ai.koog.agents.example.tone.ToneTools.ToneTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.EventHandlerConfig
import ai.koog.agents.testing.feature.withTesting
import ai.koog.agents.testing.tools.getMockExecutor

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.LLModel
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertEquals

class ToneAgentTest {

    // ToDo test the tools themselves, mocking the LLM responses.

    /**
     * Test that imitates the agent calls of corresponding ToneTools based on the input text
     */
    @Test
    @Disabled("Requires a working LLM API key")
    fun testToneAgent() = runTest {
        // Create a list to track tool calls
        val toolCalls = mutableListOf<String>()
        var result: Any? = null

        // Create a tool registry
        val toolRegistry = ToolRegistry {
            // Special tool, required with this type of agent.
            tool(SayToUser)

            with(ToneTools) {
                tools()
            }
        }

        // Create an event handler
        val eventHandlerConfig: EventHandlerConfig.() -> Unit = {
            onToolCallStarting { eventContext ->
                println("[DEBUG_LOG] Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
                toolCalls.add(eventContext.tool.name)
            }

            onAgentExecutionFailed { eventContext ->
                println(
                    "[DEBUG_LOG] An error occurred: ${eventContext.throwable.message}\n${eventContext.throwable.stackTraceToString()}"
                )
            }

            onAgentCompleted { eventContext ->
                println("[DEBUG_LOG] Result: ${eventContext.result}")
                result = eventContext.result
            }
        }

        val positiveText = "I love this product!"
        val negativeText = "Awful service, hate the app."
        val defaultText = "I don't know how to answer this question."

        val positiveResponse = "The text has a positive tone."
        val negativeResponse = "The text has a negative tone."
        val neutralResponse = "The text has a neutral tone."

        val mockLLMApi = getMockExecutor(toolRegistry) {
            // Set up LLM responses for different input texts
            mockLLMToolCall(NeutralToneTool, ToneTool.Args(defaultText)) onRequestEquals defaultText
            mockLLMToolCall(PositiveToneTool, ToneTool.Args(positiveText)) onRequestEquals positiveText
            mockLLMToolCall(NegativeToneTool, ToneTool.Args(negativeText)) onRequestEquals negativeText

            // Mock the behavior that LLM responds just tool responses once the tools returned smth.
            mockLLMAnswer(positiveResponse) onRequestContains positiveResponse
            mockLLMAnswer(negativeResponse) onRequestContains negativeResponse
            mockLLMAnswer(neutralResponse) onRequestContains neutralResponse

            mockLLMAnswer(defaultText).asDefaultResponse

            // Tool mocks:
            mockTool(PositiveToneTool) alwaysTells {
                toolCalls += "Positive tone tool called"

                positiveResponse
            }
            mockTool(NegativeToneTool) alwaysTells {
                toolCalls += "Negative tone tool called"

                negativeResponse
            }
            mockTool(NeutralToneTool) alwaysTells {
                toolCalls += "Neutral tone tool called"

                neutralResponse
            }
        }

        // Create strategy
        val strategy = toneStrategy("tone_analysis")

        // Create agent config
        val agentConfig = AIAgentConfig(
            prompt = prompt("test-agent") {
                system(
                    """
                    You are an question answering agent with access to the tone analysis tools.
                    You need to answer 1 question with the best of your ability.
                    Be as concise as possible in your answers.
                    DO NOT ANSWER ANY QUESTIONS THAT ARE BESIDES PERFORMING TONE ANALYSIS!
                    DO NOT HALLUCINATE!
                    """.trimIndent()
                )
            },
            model = mockk<LLModel>(relaxed = true),
            maxAgentIterations = 10
        )

        // Create the agent
        val agent = AIAgent(
            promptExecutor = mockLLMApi,
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry,
        ) {
            withTesting()
            install(EventHandler, eventHandlerConfig)
        }

        // Test positive text
        agent.run(positiveText)
        assertEquals("The text has a positive tone.", result, "Positive tone result should match")
        assertEquals(1, toolCalls.size, "One tool is expected to be called")

        // Test negative text
        agent.run(negativeText)
        assertEquals("The text has a negative tone.", result, "Negative tone result should match")
        assertEquals(2, toolCalls.size, "Two tools are expected to be called")

        // Test neutral text
        agent.run(defaultText)
        assertEquals("The text has a neutral tone.", result, "Neutral tone result should match")
        assertEquals(3, toolCalls.size, "Three tools are expected to be called")
    }
}
