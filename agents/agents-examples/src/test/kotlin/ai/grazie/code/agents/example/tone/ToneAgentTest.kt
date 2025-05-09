package ai.grazie.code.agents.example.tone

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.example.tone.ToneTools.NegativeToneTool
import ai.grazie.code.agents.example.tone.ToneTools.NeutralToneTool
import ai.grazie.code.agents.example.tone.ToneTools.PositiveToneTool
import ai.grazie.code.agents.example.tone.ToneTools.ToneTool
import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.api.SayToUser
import ai.grazie.code.agents.testing.feature.withTesting
import ai.grazie.code.agents.testing.tools.getMockExecutor
import ai.grazie.code.agents.testing.tools.mockLLMAnswer
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.LLModel
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
        var result: String? = null

        // Create a tool registry
        val toneStageName = "tone_analysis"
        val toolRegistry = ToolRegistry {
            stage(toneStageName) {
                // Special tool, required with this type of agent.
                tool(SayToUser)

                with(ToneTools) {
                    tools()
                }
            }
        }

        // Create an event handler
        val eventHandler = EventHandler {
            onToolCall { stage, tool, args ->
                println("[DEBUG_LOG] Tool called: stage ${stage.name}, tool ${tool.name}, args $args")
                toolCalls.add(tool.name)
            }

            handleError {
                println("[DEBUG_LOG] An error occurred: ${it.message}\n${it.stackTraceToString()}")
                true
            }

            handleResult {
                println("[DEBUG_LOG] Result: $it")
                result = it
            }
        }

        val positiveText = "I love this product!"
        val negativeText = "Awful service, hate the app."
        val defaultText = "I don't know how to answer this question."

        val positiveResponse = "The text has a positive tone."
        val negativeResponse = "The text has a negative tone."
        val neutralResponse = "The text has a neutral tone."

        val mockLLMApi = getMockExecutor(toolRegistry, eventHandler) {
            // Set up LLM responses for different input texts
            mockLLMToolCall(NeutralToneTool, ToneTool.Args(defaultText)) onRequestEquals defaultText
            mockLLMToolCall(PositiveToneTool, ToneTool.Args(positiveText)) onRequestEquals positiveText
            mockLLMToolCall(NegativeToneTool, ToneTool.Args(negativeText)) onRequestEquals negativeText

            // Mock the behaviour that LLM responds just tool responses once the tools returned smth.
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
        val strategy = toneStrategy("tone_analysis", toolRegistry, toneStageName)

        // Create agent config
        val agentConfig = LocalAgentConfig(
            prompt = prompt(mockk<LLModel>(relaxed = true), "test-agent") {
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
            maxAgentIterations = 10
        )

        // Create the agent
        val agent = AIAgentBase(
            promptExecutor = mockLLMApi,
            toolRegistry = toolRegistry,
            strategy = strategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            cs = this
        ) {
            withTesting()
        }

        // Test positive text
        agent.run(positiveText)
        assertEquals("The text has a positive tone.", result, "Positive tone result should match")
        assertEquals(1, toolCalls.size, "One tool is expected to be called")

        // Test negative text
        agent.run(negativeText)
        assertEquals("The text has a negative tone.", result, "Negative tone result should match")
        assertEquals(2, toolCalls.size, "Two tools are expected to be called")

        //Test neutral text
        agent.run(defaultText)
        assertEquals("The text has a neutral tone.", result, "Neutral tone result should match")
        assertEquals(3, toolCalls.size, "Three tools are expected to be called")

    }
}
