package ai.grazie.code.agents.core.calculator

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.KotlinAIAgent
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.agent.entity.LocalAgentStrategy
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.nodeExecuteTool
import ai.grazie.code.agents.core.dsl.extension.nodeLLMSendStageInput
import ai.grazie.code.agents.core.dsl.extension.onAssistantMessage
import ai.grazie.code.agents.core.dsl.extension.onToolCall
import ai.grazie.code.agents.core.agent.entity.ToolSelectionStrategy
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// TODO make tests multiplatform, remove dependency on examples
class CalculatorTest {
    val calculatorStageName = "calculator"

    val tools = ToolRegistry {
        stage(calculatorStageName) {
            with(CalculatorTools) {
                tools()
            }
        }
    }

    private suspend fun TestScope.runBasicCalculationsWithAgent(strategy: LocalAgentStrategy): AgentResult {
        // Store calculation results
        val results = mutableListOf<String?>()
        val toolCalls = mutableListOf<String>()
        val errors = mutableListOf<String>()

        // Create event handler for logging
        val eventHandler = EventHandler {
            onToolCall { stage, tool, arguments ->
                println("[DEBUG_LOG] Tool `${tool.name}` was called with arguments $arguments")
                toolCalls.add(tool.name)
            }

            handleError { exception ->
                println("[DEBUG_LOG] Error happened: ${exception.message}")
                errors.add(exception.message ?: "Unknown error")
                true
            }

            handleResult {
                results += it
            }
        }


        // Create agent config with proper prompt
        val agentConfig = LocalAgentConfig(
            prompt = prompt(OpenAIModels.GPT4o, "test") {
                system(
                    """
                    You are an question answering agent with access to the calculator tools.
                    You need to answer 1 question with the best of your ability.
                    Be as concise as possible in your answers, and only return the number in your final answer.
                    Do not apply any locale-specific formatting to the result.
                    DO NOT ANSWER ANY QUESTIONS THAT ARE BESIDES PERFORMING CALCULATIONS!
                    DO NOT HALLUCINATE!
                """.trimIndent()
                )
            },
            maxAgentIterations = 10
        )

        // Create the runner
        val agent = KotlinAIAgent(
            promptExecutor = CalculatorChatExecutor,
            toolRegistry = tools,
            strategy = strategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            cs = this
        )

        // Run calculations
        agent.run("add 2 and 2")
        agent.run("subtract 5 from 5")
        agent.run("multiply 2 by 3")
        agent.run("divide 6 by 3")

        return AgentResult(results, toolCalls, errors)
    }

    private fun verifySuccessAgentResult(agentResult: AgentResult) {
        val (results, toolCalls, errors) = agentResult

        // Verify results
        assertEquals(4, results.size, "Expected 4 results")
        assertEquals("4.0", results[0], "2 + 2 should be 4.0")
        assertEquals("0.0", results[1], "5 - 5 should be 0.0")
        assertEquals("6.0", results[2], "2 * 3 should be 6.0")
        assertEquals("2.0", results[3], "6 / 3 should be 2.0")

        // Verify that all tool types were used
        assertTrue(toolCalls.contains(CalculatorTools.PlusTool.name), "Expected plus tool to be called")
        assertTrue(toolCalls.contains(CalculatorTools.MinusTool.name), "Expected minus tool to be called")
        assertTrue(toolCalls.contains(CalculatorTools.MultiplyTool.name), "Expected multiply tool to be called")
        assertTrue(toolCalls.contains(CalculatorTools.DivideTool.name), "Expected divide tool to be called")

        // Verify errors
        assertTrue(errors.isEmpty(), "Expected no errors, but got: $errors")
    }

    @Test
    fun testBasicCalculations() = runTest {
        // Create a simple calculator agent
        val calculatorStrategy = strategy("test") {
            stage(
                name = calculatorStageName,
                requiredTools = tools.stagesToolDescriptors.getValue(calculatorStageName)
            ) {
                val nodeSendInput by nodeLLMSendStageInput()
                val nodeExecuteTool by nodeExecuteTool()

                edge(nodeStart forwardTo nodeSendInput)

                edge(
                    (nodeSendInput forwardTo nodeFinish)
                            onAssistantMessage { true }
                )

                edge(
                    (nodeSendInput forwardTo nodeExecuteTool)
                            onToolCall { true }
                )

                edge(
                    (nodeExecuteTool forwardTo nodeFinish)
                            transformed { it.content }
                )
            }
        }

        val result = runBasicCalculationsWithAgent(calculatorStrategy)

        verifySuccessAgentResult(result)
    }

    @Test
    fun testBasicCalculationsWithNestedSubgraphs() = runTest {
        val innerStrategy = strategy("inner") {
            stage(
                name = calculatorStageName,
                requiredTools = tools.stagesToolDescriptors.getValue(calculatorStageName)
            ) {
                val process by subgraph(toolSelectionStrategy = ToolSelectionStrategy.ALL) {
                    val nodeSendInput by nodeLLMSendStageInput()
                    val nodeExecuteTool by nodeExecuteTool()

                    edge(nodeStart forwardTo nodeSendInput)

                    edge(
                        (nodeSendInput forwardTo nodeFinish)
                                onAssistantMessage { true }
                    )

                    edge(
                        (nodeSendInput forwardTo nodeExecuteTool)
                                onToolCall { true }
                    )

                    edge(
                        (nodeExecuteTool forwardTo nodeFinish)
                                transformed { it.content }
                    )
                }

                edge(nodeStart forwardTo process)
                edge(process forwardTo nodeFinish)
            }
        }

        val outerStrategy = strategy("test") {
            stage(
                name = calculatorStageName,
                requiredTools = tools.stagesToolDescriptors.getValue(calculatorStageName)
            ) {
                val subAgentNode = innerStrategy

                edge(nodeStart forwardTo subAgentNode transformed { stageInput })
                edge(subAgentNode forwardTo nodeFinish)
            }
        }

        val result = runBasicCalculationsWithAgent(outerStrategy)

        verifySuccessAgentResult(result)
    }

    @Test
    fun testCalculationsWithoutTools() = runTest {
        // Create a strategy with a subgraph that has no tools
        val strategyWithNoTools = strategy("test-without-tools") {
            stage(
                name = calculatorStageName,
                requiredTools = tools.stagesToolDescriptors.getValue(calculatorStageName)
            ) {
                val process by subgraph(
                    name = "subgraph-without-tools",
                    toolSelectionStrategy = ToolSelectionStrategy.NONE
                ) {
                    val nodeSendInput by nodeLLMSendStageInput()
                    val nodeExecuteTool by nodeExecuteTool()

                    edge(nodeStart forwardTo nodeSendInput)
                    edge(
                        (nodeSendInput forwardTo nodeFinish)
                                onAssistantMessage { true }
                    )

                    edge(
                        (nodeSendInput forwardTo nodeExecuteTool)
                                onToolCall { true }
                    )

                    edge(
                        (nodeExecuteTool forwardTo nodeFinish)
                                transformed { it.content }
                    )
                }

                edge(nodeStart forwardTo process)
                edge(process forwardTo nodeFinish)
            }
        }

        val (_, toolCalls, _) = runBasicCalculationsWithAgent(strategyWithNoTools)

        assertTrue(toolCalls.isEmpty(), "Expected 0 tool calls since no tools were available")
    }

    @Test
    fun testCalculationsWithCustomTools() = runTest {
        // Create a strategy with a subgraph that has no tools
        val strategyWithNoTools = strategy("test-with-custom-tools") {
            stage(
                name = calculatorStageName,
                requiredTools = tools.stagesToolDescriptors.getValue(calculatorStageName)
            ) {
                val process by subgraph(
                    name = "subgraph-with-custom-tools",
                    toolSelectionStrategy = ToolSelectionStrategy.Tools(
                        listOf(CalculatorTools.PlusTool.descriptor, CalculatorTools.MinusTool.descriptor)
                    )
                ) {
                    val nodeSendInput by nodeLLMSendStageInput()
                    val nodeExecuteTool by nodeExecuteTool()

                    edge(nodeStart forwardTo nodeSendInput)
                    edge(
                        (nodeSendInput forwardTo nodeFinish)
                                onAssistantMessage { true }
                    )

                    edge(
                        (nodeSendInput forwardTo nodeExecuteTool)
                                onToolCall { true }
                    )

                    edge(
                        (nodeExecuteTool forwardTo nodeFinish)
                                transformed { it.content }
                    )
                }

                edge(nodeStart forwardTo process)
                edge(process forwardTo nodeFinish)
            }
        }

        val (_, toolCalls, _) = runBasicCalculationsWithAgent(strategyWithNoTools)

        assertEquals(2, toolCalls.size, "Expected 2 tool calls since 2 tools were available")
    }

    private data class AgentResult(
        val results: List<String?>,
        val toolCalls: List<String>,
        val errors: List<String>
    )
}
