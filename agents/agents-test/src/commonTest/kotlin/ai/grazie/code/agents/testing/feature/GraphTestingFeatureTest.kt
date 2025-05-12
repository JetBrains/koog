package ai.grazie.code.agents.testing.feature

import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.environment.ReceivedToolResult
import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.testing.tools.getMockExecutor
import ai.grazie.code.agents.testing.tools.mockLLMAnswer
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the Testing feature.
 */
class GraphTestingFeatureTest {

    @Test
    fun testMultiStageAgentStructure() = runTest {
        val strategy = strategy("test") {
            stage("first") {
                val callLLM by nodeLLMRequest(allowToolCalls = false)
                val executeTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()
                val giveFeedback by node<String, String> { input ->
                    llm.writeSession {
                        updatePrompt {
                            user("Call tools! Don't chat!")
                        }
                    }
                    input
                }

                edge(nodeStart forwardTo callLLM transformed { stageInput })
                edge(callLLM forwardTo executeTool onToolCall { true })
                edge(callLLM forwardTo giveFeedback onAssistantMessage { true })
                edge(giveFeedback forwardTo giveFeedback onAssistantMessage { true })
                edge(giveFeedback forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo nodeFinish transformed { it.content })
            }

            stage("second") {
                edge(nodeStart forwardTo nodeFinish transformed { stageInput })
            }
        }

        val toolRegistry = ToolRegistry {
            stage("first") {
                tool(DummyTool)
                tool(CreateTool)
                tool(SolveTool)
            }
            stage("second") {
                tool(DummyTool)
            }
        }

        val mockLLMApi = getMockExecutor(toolRegistry) {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
        }

        val basePrompt = prompt("test") {}

        AIAgentBase(
            promptExecutor = mockLLMApi,
            strategy = strategy,
            cs = this@runTest,
            agentConfig = LocalAgentConfig(prompt = basePrompt, model = OpenAIModels.GPT4o, maxAgentIterations = 100),
            toolRegistry = toolRegistry,
            eventHandler = EventHandler {}
        ) {
            testGraph {
                assertStagesOrder("first", "second")

                stage("first") {
                    val start = startNode()
                    val finish = finishNode()

                    val askLLM = assertNodeByName<String, Message.Response>("callLLM")
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
                    val giveFeedback = assertNodeByName<Any?, Any?>("giveFeedback")

                    assertReachable(start, askLLM)
                    assertReachable(askLLM, callTool)

                    assertNodes {
                        askLLM withInput "Hello" outputs Message.Assistant("Hello!")
                        askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))

                        callTool withInput toolCallSignature(
                            SolveTool,
                            SolveTool.Args("solve")
                        ) outputs toolResult(SolveTool, "solved")

                        callTool withInput toolCallSignature(
                            CreateTool,
                            CreateTool.Args("solve")
                        ) outputs toolResult(CreateTool, "created")
                    }

                    assertEdges {
                        askLLM withOutput Message.Assistant("Hello!") goesTo giveFeedback
                        askLLM withOutput toolCallMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
                    }
                }

                stage("second") {
                    // Empty stage for demonstration
                }
            }
        }
    }


    @Test
    fun testTestingFeatureAPI() {
        // This test demonstrates the API of Testing feature
        // In a real test, you would use an actual KotlinAIAgent

        // Create a Config instance directly to test the API
        val config = Testing.Config().apply {
            assertStagesOrder("first", "second")

            stage("first") {
                val start = startNode()
                val finish = finishNode()

                val askLLM = assertNodeByName<String, Message.Response>("callLLM")
                val callTool = assertNodeByName<Message.Tool.Call, Message.Tool.Result>("executeTool")
                val giveFeedback = assertNodeByName<Any?, Any?>("giveFeedback")

                assertReachable(start, askLLM)
                assertReachable(askLLM, callTool)
                assertReachable(callTool, giveFeedback)
                assertReachable(giveFeedback, finish)
            }

            stage("second") {
                val start = startNode()
                val finish = finishNode()

                assertReachable(start, finish)
            }
        }

        // Verify that the config was created correctly
        assertEquals(2, config.getStages().size)
        assertEquals(listOf("first", "second"), config.getStagesOrder())

        // Verify the first stage
        val firstStage = config.getStages().find { it.name == "first" }
        assertEquals("first", firstStage?.name)
        assertEquals(3, firstStage?.nodes?.size)
        assertEquals(4, firstStage?.reachabilityAssertions?.size)

        // Verify the second stage
        val secondStage = config.getStages().find { it.name == "second" }
        assertEquals("second", secondStage?.name)
        assertEquals(0, secondStage?.nodes?.size)
        assertEquals(1, secondStage?.reachabilityAssertions?.size)
    }
}
