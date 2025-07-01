package ai.koog.agents.testing.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the Testing feature.
 */
class GraphTestingFeatureTest {

    @Test
    fun testMultiSubgraphAgentStructure() = runTest {
        val strategy = strategy("test") {
            val firstSubgraph by subgraph(
                "first",
                tools = listOf(DummyTool, CreateTool, SolveTool)
            ) {
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

                edge(nodeStart forwardTo callLLM)
                edge(callLLM forwardTo executeTool onToolCall { true })
                edge(callLLM forwardTo giveFeedback onAssistantMessage { true })
                edge(giveFeedback forwardTo giveFeedback onAssistantMessage { true })
                edge(giveFeedback forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo nodeFinish transformed { it.content })
            }

            val secondSubgraph by subgraph<String, String>("second") {
                edge(nodeStart forwardTo nodeFinish)
            }

            edge(nodeStart forwardTo firstSubgraph)
            edge(firstSubgraph forwardTo secondSubgraph)
            edge(secondSubgraph forwardTo nodeFinish)
        }

        val toolRegistry = ToolRegistry {
            tool(DummyTool)
            tool(CreateTool)
            tool(SolveTool)
        }

        val mockLLMApi = getMockExecutor(toolRegistry) {
            mockLLMAnswer("Hello!") onRequestContains "Hello"
            mockLLMToolCall(CreateTool, CreateTool.Args("solve")) onRequestEquals "Solve task"
        }

        val basePrompt = prompt("test") {}

        AIAgent(
            promptExecutor = mockLLMApi,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt = basePrompt, model = OpenAIModels.Chat.GPT4o, maxAgentIterations = 100),
            toolRegistry = toolRegistry
        ) {
            testGraph<String, String>("test") {
                val firstSubgraph = assertSubgraphByName<Unit, String>("first")
                val secondSubgraph = assertSubgraphByName<Unit, String>("second")

                assertEdges {
                    startNode() alwaysGoesTo firstSubgraph
                    firstSubgraph alwaysGoesTo secondSubgraph
                    secondSubgraph alwaysGoesTo finishNode()
                }

                verifySubgraph(firstSubgraph) {
                    val start = startNode()
                    val finish = finishNode()

                    val askLLM = assertNodeByName<String, Message.Response>("callLLM")
                    val callTool = assertNodeByName<Message.Tool.Call, ReceivedToolResult>("executeTool")
                    val giveFeedback = assertNodeByName<Any?, Any?>("giveFeedback")

                    assertReachable(start, askLLM)
                    assertReachable(askLLM, callTool)

                    assertNodes {
                        askLLM withInput "Hello" outputs assistantMessage("Hello!")
                        askLLM withInput "Solve task" outputs toolCallMessage(CreateTool, CreateTool.Args("solve"))

                        callTool withInput toolCallMessage(
                            SolveTool,
                            SolveTool.Args("solve")
                        ) outputs toolResult(SolveTool, "solved")

                        callTool withInput toolCallMessage(
                            CreateTool,
                            CreateTool.Args("solve")
                        ) outputs toolResult(CreateTool, "created")
                    }

                    assertEdges {
                        askLLM withOutput assistantMessage("Hello!") goesTo giveFeedback
                        askLLM withOutput toolCallMessage(CreateTool, CreateTool.Args("solve")) goesTo callTool
                    }
                }
            }
        }
    }


    @Test
    fun testTestingFeatureAPI() {
        // This test demonstrates the API of Testing feature
        // In a real test, you would use an actual AIAgent

        // Create a Config instance directly to test the API
        val config = Testing.Config().apply {
            verifyStrategy<String, String>("test") {
                val first = assertSubgraphByName<String, String>("first")
                val second = assertSubgraphByName<String, String>("second")
                verifySubgraph(first) {
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

                verifySubgraph(second) {
                    val start = startNode()
                    val finish = finishNode()

                    assertReachable(start, finish)
                }
            }
        }

        // Verify that the config was created correctly
        assertEquals(2, config.getAssertions().subgraphAssertions.size)

        // Verify the first stage
        val firstSubgraphAssertion = config.getAssertions().subgraphAssertions.find { it.subgraphRef.name == "first" }
        assertEquals("first", firstSubgraphAssertion?.subgraphRef?.name)
        assertEquals(3, firstSubgraphAssertion?.graphAssertions?.nodes?.size)
        assertEquals(4, firstSubgraphAssertion?.graphAssertions?.reachabilityAssertions?.size)

        // Verify the second stage
        val secondSubgraphAssertion = config.getAssertions().subgraphAssertions.find { it.subgraphRef.name == "second" }
        assertEquals("second", secondSubgraphAssertion?.subgraphRef?.name)
        assertEquals(0, secondSubgraphAssertion?.graphAssertions?.nodes?.size)
        assertEquals(1, secondSubgraphAssertion?.graphAssertions?.reachabilityAssertions?.size)
    }
}
