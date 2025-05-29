package ai.koog.integration.tests

import ai.koog.integration.tests.tools.AnswerVerificationTool
import ai.koog.integration.tests.tools.GenericParameterTool
import ai.koog.integration.tests.tools.GeographyQueryTool
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.integration.tests.utils.TestUtils.runWithRetry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@EnabledOnOs(OS.LINUX, OS.MAC)
@ExtendWith(OllamaTestFixtureExtension::class)
class OllamaAgentIntegrationTest {
    companion object {
        @field:InjectOllamaTestFixture
        private lateinit var fixture: OllamaTestFixture
        private val executor get() = fixture.executor
        private val model get() = fixture.model
    }

    private fun createTestStrategy() = strategy("test-ollama") {
        val askCapitalSubgraph by subgraph<String, String>("ask-capital") {
            val definePrompt by node<Unit, Unit> {
                llm.writeSession {
                    model = OllamaModels.Meta.LLAMA_3_2
                    rewritePrompt {
                        prompt("test-ollama") {
                            system(
                                """
                                        You are a top-tier geographical assistant. " +
                                            ALWAYS communicate to user via tools!!!
                                            ALWAYS use tools you've been provided.
                                            ALWAYS generate valid JSON responses.
                                            ALWAYS call tool correctly, with valid arguments.
                                            NEVER provide tool call in result body.
                                            
                                            Example tool call:
                                            {
                                                "id":"ollama_tool_call_3743609160",
                                                "tool":"geography_query_tool",
                                                "content":{"query":"capital of France"}
                                            }
                                            """.trimIndent()
                            )
                        }
                    }
                }
            }

            val callLLM by nodeLLMRequest(allowToolCalls = true)
            val callTool by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo definePrompt transformed {})
            edge(definePrompt forwardTo callLLM transformed { agentInput })
            edge(callLLM forwardTo callTool onToolCall { true })
            edge(callTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo callTool onToolCall { true })
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        val askVerifyAnswer by subgraph<String, String>("verify-answer") {
            val definePrompt by node<Unit, Unit> {
                llm.writeSession {
                    model = OllamaModels.Meta.LLAMA_3_2
                    rewritePrompt {
                        prompt("test-ollama") {
                            system(
                                """"
                                        You are a top-tier assistant.
                                        ALWAYS communicate to user via tools!!!
                                        ALWAYS use tools you've been provided.
                                        ALWAYS generate valid JSON responses.
                                        ALWAYS call tool correctly, with valid arguments.
                                        NEVER provide tool call in result body.
                                      
                                        Example tool call:
                                        {
                                            "id":"ollama_tool_call_3743609160"
                                            "tool":"answer_verification_tool"
                                            "content":{"answer":"Paris"}
                                        }.""".trimIndent()
                            )
                        }
                    }
                }
            }

            val callLLM by nodeLLMRequest(allowToolCalls = true)
            val callTool by nodeExecuteTool()
            val sendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo definePrompt transformed {})
            edge(definePrompt forwardTo callLLM transformed { agentInput })
            edge(callLLM forwardTo callTool onToolCall { true })
            edge(callTool forwardTo sendToolResult)
            edge(sendToolResult forwardTo callTool onToolCall { true })
            edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(callLLM forwardTo nodeFinish onAssistantMessage { true })
        }

        nodeStart then askCapitalSubgraph then askVerifyAnswer then nodeFinish
    }


    private fun createToolRegistry(): ToolRegistry {
        return ToolRegistry {
            tool(GeographyQueryTool)
            tool(AnswerVerificationTool)
            tool(GenericParameterTool)
        }
    }

    private fun createAgent(
        executor: PromptExecutor, strategy: AIAgentStrategy, toolRegistry: ToolRegistry
    ): AIAgent {
        val promptsAndResponses = mutableListOf<String>()

        return AIAgent(
            promptExecutor = executor,
            strategy = strategy,
            agentConfig = AIAgentConfig(prompt("test-ollama") {}, model, 15),
            toolRegistry = toolRegistry
        ) {
            install(EventHandler) {
                onToolCall = { tool, arguments ->
                    println(
                        "Calling tool ${tool.name} with arguments ${
                            arguments.toString().lines().first().take(100)
                        }"
                    )
                }

                onBeforeLLMCall = { prompt, tools ->
                    val promptText = prompt.messages.joinToString { "${it.role.name}: ${it.content}" }
                    val toolsText = tools.joinToString{ it.name }
                    println("Prompt with tools:\n$promptText\nAvailable tools:\n$toolsText")
                    promptsAndResponses.add("PROMPT_WITH_TOOLS: $promptText")
                }

                onAfterLLMCall = { responses ->
                    val responseText = "[${responses.joinToString { "${it.role.name}: ${it.content}" }}]"
                    println("LLM Call response: $responseText")
                    promptsAndResponses.add("RESPONSE: $responseText")
                }

                onAgentFinished = { _, _ ->
                    println("Agent execution finished")
                }
            }
        }
    }

    @Test
    fun integration_testOllamaAgentClearContext() = runTest(timeout = 600.seconds) {
        val strategy = createTestStrategy()
        val toolRegistry = createToolRegistry()
        val agent = createAgent(executor, strategy, toolRegistry)

        val result = runWithRetry { agent.runAndGetResult("What is the capital of France?") }

        assertNotNull(result, "Result should not be empty")
        assertTrue(result.isNotEmpty(), "Result should not be empty")
        assertContains(result, "Paris", ignoreCase = true, "Result should contain the answer 'Paris'")
    }
}
