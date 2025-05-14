package ai.jetbrains.code.prompt.executor.ollama

import ai.grazie.code.agents.core.agent.AIAgent
import ai.grazie.code.agents.core.agent.config.AIAgentConfig
import ai.grazie.code.agents.core.agent.entity.AIAgentStorageKey
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.*
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.features.eventHandler.feature.EventHandler
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.executor.ollama.client.OllamaClient
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Disabled("Disabled until having a docker image with Ollama running")
class OllamaIntegrationTest {
    private val model = OllamaModels.Meta.LLAMA_3_2
    val client = OllamaClient()
    val executor = OllamaPromptExecutor(client)

    private fun createTestStrategy(policyName: String) =
        strategy("test-ollama-$policyName") {
            val agentInputKey = AIAgentStorageKey<String>("agentInput")

            val askCapitalSubgraph by subgraph("ask-capital") {
                val definePrompt by node<Unit, Unit> {
                    llm.writeSession {
                        model = OllamaModels.Meta.LLAMA_3_2
                        rewritePrompt {
                            prompt("test-ollama") {
                                system("You are a helpful assistant. You need to answer the question about the capital of France. Before answering, use the generic_parameter_tool with a required argument 'requiredArg' set to 'ask-capital' and an optional argument 'optionalArg' if you want. Also, use the geography_query_tool with a required argument 'query' set to 'capital of France'.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePrompt)
                edge(definePrompt forwardTo callLLM transformed { storage.get(agentInputKey).orEmpty() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true } transformed {})
            }

            val askVerifyAnswer by subgraph("verify-answer") {
                val definePrompt by node<Unit, Unit> {
                    llm.writeSession {
                        model = OllamaModels.Meta.LLAMA_3_2
                        rewritePrompt {
                            prompt("test-ollama") {
                                system("You are a helpful assistant. You need to verify that the answer about the capital of France is correct. The correct answer is Paris. Before verifying, use the generic_parameter_tool with a required argument 'requiredArg' set to 'verify-answer' and an optional argument 'optionalArg' if you want. Also, use the answer_verification_tool with a required argument 'answer' set to 'Paris'.")
                            }
                        }
                    }
                }

                val callLLM by nodeLLMRequest(allowToolCalls = true)
                val callTool by nodeExecuteTool()
                val sendToolResult by nodeLLMSendToolResult()

                edge(nodeStart forwardTo definePrompt)
                edge(definePrompt forwardTo callLLM transformed { storage.get(agentInputKey).orEmpty() })
                edge(callLLM forwardTo callTool onToolCall { true })
                edge(callTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo callTool onToolCall { true })
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val nodeSaveInput by node<String, Unit>("save_input") {
                storage.set(agentInputKey, it)
            }

            nodeStart then nodeSaveInput then askCapitalSubgraph then askVerifyAnswer then nodeFinish
        }


    private fun createStageSpecificToolRegistry(): ToolRegistry {
        return ToolRegistry {
            tool(GeographyQueryTool)
            tool(AnswerVerificationTool)
            tool(GenericParameterTool)
        }
    }

    private fun createAgent(
        executor: OllamaPromptExecutor,
        strategy: ai.grazie.code.agents.core.agent.entity.AIAgentStrategy,
        toolRegistry: ToolRegistry
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

                onBeforeLLMCall = { prompt ->
                    val promptText = prompt.messages.joinToString("\n") { "${it.role}: ${it.content}" }
                    println("Prompt:\n$promptText")
                    promptsAndResponses.add("PROMPT: $promptText")
                }

                onAfterLLMCall = { response ->
                    println("Response: $response")
                    promptsAndResponses.add("RESPONSE: $response")
                }

                onBeforeLLMWithToolsCall = { prompt, tools ->
                    val promptText = prompt.messages.joinToString("\n") { "${it.role}: ${it.content}" }
                    val toolsText = tools.joinToString("\n") { it.name }
                    println("Prompt with tools:\n$promptText\nAvailable tools:\n$toolsText")
                    promptsAndResponses.add("PROMPT_WITH_TOOLS: $promptText")
                }

                onAfterLLMWithToolsCall = { responses, tools ->
                    val responseText = responses.joinToString("\n") { it.content }
                    println("Response with tools: $responseText")
                    promptsAndResponses.add("RESPONSE_WITH_TOOLS: $responseText")
                }

                onAgentFinished = { _, _ ->
                    println("Agent execution finished")
                }
            }
        }
    }

    @Test
    fun integration_testOllamaAgentClearContext() = runTest {
        val strategy = createTestStrategy("clear")
        val toolRegistry = createStageSpecificToolRegistry()
        val agent = createAgent(executor, strategy, toolRegistry)

        val result = agent.runAndGetResult("What is the capital of France?")

        assertNotNull(result, "Result should not be empty")
        assertTrue(result.isNotEmpty(), "Result should not be empty")
    }

    @Test
    fun integration_testOllamaAgentPersistContext() = runTest {
        val strategy = createTestStrategy("persist")
        val toolRegistry = createStageSpecificToolRegistry()
        val agent = createAgent(executor, strategy, toolRegistry)

        val result = agent.runAndGetResult("What is the capital of France?")

        assertNotNull(result, "Result should not be empty")
        assertTrue(result.isNotEmpty(), "Result should not be empty")
        assertContains(result, "Paris", ignoreCase = true, "Result should contain the answer 'Paris'")
    }

    @Test
    fun integration_testOllamaAgentCompressContext() = runTest {
        val strategy = createTestStrategy("compress")
        val toolRegistry = createStageSpecificToolRegistry()
        val agent = createAgent(executor, strategy, toolRegistry)

        val result = agent.runAndGetResult("What is the capital of France?")

        assertNotNull(result, "Result should not be empty")
        assertTrue(result.isNotEmpty(), "Result should not be empty")
        assertContains(result, "Paris", ignoreCase = true, "Result should contain the answer 'Paris'")
    }
}
