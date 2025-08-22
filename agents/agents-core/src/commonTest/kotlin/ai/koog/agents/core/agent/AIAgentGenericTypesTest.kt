package ai.koog.agents.core.agent

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIAgentGenericTypesTest {

    @Test
    fun testGenericInputOutputTypes() = runTest {
        data class CustomInput(val query: String)
        data class CustomOutput(val result: String, val confidence: Double)

        val mockResponse = "This is a mock response"
        val mockExecutor = getMockExecutor {
            mockLLMAnswer(mockResponse).asDefaultResponse
        }

        val customStrategy = strategy<CustomInput, CustomOutput>("custom-strategy") {
            val processInput = { input: CustomInput -> input.query }
            val processOutput = { output: Message.Response -> CustomOutput(result = output.content, confidence = 0.95) }

            val callLLM by nodeLLMRequest()

            edge(nodeStart forwardTo callLLM transformed { input -> processInput(input) })
            edge(callLLM forwardTo nodeFinish transformed { output -> processOutput(output) })
        }

        val agent = AIAgent<CustomInput, CustomOutput>(
            promptExecutor = mockExecutor,
            strategy = customStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("custom-types-test") {
                    system("You are a helpful assistant.")
                },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 5
            )
        )

        val result = agent.run(CustomInput(query = "What is the capital of France?"))

        assertEquals(mockResponse, result.result)
        assertEquals(0.95, result.confidence)
    }

    @Test
    fun testPrimitiveGenericTypes() = runTest {
        val customStrategy = strategy<Int, Boolean>("int-to-bool-strategy") {
            val convertToString = { input: Int -> "Is $input an even number?" }

            val parseResponse = { output: Message.Response ->
                output.content.contains("yes", ignoreCase = true) ||
                    output.content.contains("even", ignoreCase = true)
            }

            val callLLM by nodeLLMRequest()

            edge(nodeStart forwardTo callLLM transformed { input -> convertToString(input) })
            edge(callLLM forwardTo nodeFinish transformed { output -> parseResponse(output) })
        }

        val mockExecutorForEven = getMockExecutor {
            mockLLMAnswer("Yes, 42 is an even number.").asDefaultResponse
        }

        val mockExecutorForOdd = getMockExecutor {
            mockLLMAnswer("No, 43 is an odd number.").asDefaultResponse
        }

        val evenAgent = AIAgent<Int, Boolean>(
            promptExecutor = mockExecutorForEven,
            strategy = customStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("number-test") {
                    system("You are a math assistant.")
                },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 5
            )
        )

        val oddAgent = AIAgent<Int, Boolean>(
            promptExecutor = mockExecutorForOdd,
            strategy = customStrategy,
            agentConfig = AIAgentConfig(
                prompt = prompt("number-test") {
                    system("You are a math assistant.")
                },
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 5
            )
        )

        val resultEven = evenAgent.run(42)
        val resultOdd = oddAgent.run(43)

        assertTrue(resultEven)
        assertTrue(!resultOdd)
    }
}
