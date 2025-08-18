package ai.koog.agents.features.opentelemetry.integration

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.assertMapsEqual
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.features.opentelemetry.mock.TestGetWeatherTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class TraceStructureTestBase(private val openTelemetryConfigurator: OpenTelemetryConfig.() -> Unit) {

    @Test
    fun testSingleLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->

            val strategy = strategy("single-llm-call-strategy") {
                val llmRequest by nodeLLMRequest("llm-call")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val mockResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val promptExecutor = getMockExecutor {
                mockLLMAnswer(mockResponse) onRequestEquals userPrompt
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = promptExecutor,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter,
            )

            val actualSpans = mockSpanExporter.collectedSpans

            assertTrue { actualSpans.filter { it.name.startsWith("run.") }.size == 1 }
            assertTrue { actualSpans.filter { it.name == "node.__start__" }.size == 1 }
            assertTrue { actualSpans.filter { it.name == "node.llm-call" }.size == 1 }
            assertTrue { actualSpans.filter { it.name == "llm.test-prompt-id" }.size == 1 }

            val runNode = actualSpans.first { it.name.startsWith("run.") }
            val startNode = actualSpans.first { it.name == "node.__start__" }
            val llmNode = actualSpans.first { it.name == "node.llm-call" }
            val llmGeneration = actualSpans.first { it.name == "llm.test-prompt-id" }

            assertTrue { runNode.spanId == startNode.parentSpanId }
            assertTrue { runNode.spanId == llmNode.parentSpanId }
            assertTrue { llmNode.spanId == llmGeneration.parentSpanId }

            val actualSpanAttributes = llmGeneration.attributes.asMap()
                .map { (key, value) -> key.key to value }
                .toMap()

            // Assert expected LLM Call Span (IntentSpan) attributes for Langfuse/Weave

            val expectedAttributes = mapOf(
                // General attributes
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.model" to model.id,
                "gen_ai.request.temperature" to 0.4,

                // Langfuse/Weave specific attributes
                "gen_ai.prompt.0.role" to "system",
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to "user",
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.completion.0.role" to "assistant",
                "gen_ai.completion.0.content" to mockResponse,
            )

            assertEquals(expectedAttributes.size, actualSpanAttributes.size)
            assertMapsEqual(expectedAttributes, actualSpanAttributes)
        }
    }

    @Test
    fun testLLMCallToolCallLLMCall() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("llm-tool-llm-strategy") {
                val llmRequest by nodeLLMRequest("LLM Request", allowToolCalls = true)
                val executeTool by nodeExecuteTool("Execute Tool")
                val sendToolResult by nodeLLMSendToolResult("Send Tool Result")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool onToolCall { true })
                edge(executeTool forwardTo sendToolResult)
                edge(sendToolResult forwardTo nodeFinish onAssistantMessage { true })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4
            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris?"
            val toolCallArgs = TestGetWeatherTool.Args("Paris")
            val toolResponse = TestGetWeatherTool.DEFAULT_PARIS_RESULT
            val finalResponse = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(TestGetWeatherTool, toolCallArgs) onRequestEquals userPrompt
                mockLLMAnswer(finalResponse) onRequestContains toolResponse
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            val actualSpans = mockSpanExporter.collectedSpans

            assertTrue { actualSpans.filter { it.name == "tool.Get whether" }.size == 1 }

            val runNode = actualSpans.first { it.name.startsWith("run.") }
            val startNode = actualSpans.first { it.name == "node.__start__" }
            val llmRequestNode = actualSpans.first { it.name == "node.LLM Request" }
            val executeToolNode = actualSpans.first { it.name == "node.Execute Tool" }
            val sendToolResultNode = actualSpans.first { it.name == "node.Send Tool Result" }
            val toolCallSpan = actualSpans.first { it.name == "tool.Get whether" }
            val llmSpans = actualSpans.filter { it.name == "llm.test-prompt-id" }

            // All nodes should have runNode as parent
            assertTrue { runNode.spanId == startNode.parentSpanId }
            assertTrue { runNode.spanId == llmRequestNode.parentSpanId }
            assertTrue { runNode.spanId == executeToolNode.parentSpanId }
            assertTrue { runNode.spanId == sendToolResultNode.parentSpanId }

            // Check LLM Call span with the initial call and tool call request
            val actualInitialLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == llmRequestNode.spanId }
            assertNotNull(actualInitialLLMCallSpan)

            val actualInitialLLMCallSpanAttributes =
                actualInitialLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedInitialLLMCallSpansAttributes = mapOf(
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.temperature" to temperature,
                "gen_ai.request.model" to model.id,

                "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
                "gen_ai.completion.0.content" to "[{\"function\":{\"name\":\"${TestGetWeatherTool.name}\",\"arguments\":\"{\"location\":\"Paris\"}\"},\"id\":\"\",\"type\":\"function\"}]",
            )

            assertEquals(expectedInitialLLMCallSpansAttributes.size, actualInitialLLMCallSpanAttributes.size)
            assertMapsEqual(expectedInitialLLMCallSpansAttributes, actualInitialLLMCallSpanAttributes)

            // Check LLM Call span with the final LLM response after the tool is executed
            val actualFinalLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == sendToolResultNode.spanId }
            assertNotNull(actualFinalLLMCallSpan)

            val actualFinalLLMCallSpanAttributes =
                actualFinalLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedFinalLLMCallSpansAttributes = mapOf(
                "gen_ai.system" to model.provider.id,
                "gen_ai.conversation.id" to mockSpanExporter.lastRunId,
                "gen_ai.operation.name" to "chat",
                "gen_ai.request.temperature" to temperature,
                "gen_ai.request.model" to model.id,

                "gen_ai.prompt.0.role" to Message.Role.System.name.lowercase(),
                "gen_ai.prompt.0.content" to systemPrompt,
                "gen_ai.prompt.1.role" to Message.Role.User.name.lowercase(),
                "gen_ai.prompt.1.content" to userPrompt,
                "gen_ai.prompt.2.role" to Message.Role.Tool.name.lowercase(),
                "gen_ai.prompt.2.content" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
                "gen_ai.completion.0.role" to Message.Role.Assistant.name.lowercase(),
                "gen_ai.completion.0.content" to finalResponse,
            )

            assertEquals(expectedFinalLLMCallSpansAttributes.size, actualFinalLLMCallSpanAttributes.size)
            assertMapsEqual(expectedFinalLLMCallSpansAttributes, actualFinalLLMCallSpanAttributes)

            // Tool span should have executed tool node as parent
            assertTrue { executeToolNode.spanId == toolCallSpan.parentSpanId }
            val actualToolCallSpanAttributes =
                toolCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedToolCallSpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "input.value" to "{\"location\":\"Paris\"}",
                "output.value" to TestGetWeatherTool.DEFAULT_PARIS_RESULT,
            )

            assertEquals(expectedToolCallSpanAttributes.size, actualToolCallSpanAttributes.size)
            assertMapsEqual(expectedToolCallSpanAttributes, actualToolCallSpanAttributes)
        }
    }

    @Test
    fun testMultipleToolCalls() = runBlocking {
        MockSpanExporter().use { mockSpanExporter ->
            val strategy = strategy("multiple-tool-calls-strategy") {
                val llmRequest by nodeLLMRequest("Initial LLM Request", allowToolCalls = true)
                val executeTool1 by nodeExecuteTool("Execute Tool 1")
                val sendToolResult1 by nodeLLMSendToolResult("Send Tool Result 1")
                val executeTool2 by nodeExecuteTool("Execute Tool 2")
                val sendToolResult2 by nodeLLMSendToolResult("Send Tool Result 2")

                edge(nodeStart forwardTo llmRequest)
                edge(llmRequest forwardTo executeTool1 onToolCall { true })
                edge(executeTool1 forwardTo sendToolResult1)
                edge(sendToolResult1 forwardTo executeTool2 onToolCall { true })
                edge(executeTool2 forwardTo sendToolResult2)
                edge(sendToolResult2 forwardTo nodeFinish transformed { input -> input.content })
            }

            val model = OpenAIModels.Chat.GPT4o
            val temperature = 0.4

            val systemPrompt = "You are the application that predicts weather"
            val userPrompt = "What's the weather in Paris and London?"

            val toolCallArgs1 = TestGetWeatherTool.Args("Paris")
            val toolResponse1 = TestGetWeatherTool.DEFAULT_PARIS_RESULT

            val toolCallArgs2 = TestGetWeatherTool.Args("London")
            val toolResponse2 = TestGetWeatherTool.DEFAULT_LONDON_RESULT

            val finalResponse = "The weather in Paris is rainy (57°F) and in London it's cloudy (62°F)"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(TestGetWeatherTool, toolCallArgs1) onRequestEquals userPrompt
                mockLLMToolCall(TestGetWeatherTool, toolCallArgs2) onRequestContains toolResponse1
                mockLLMAnswer(finalResponse) onRequestContains toolResponse2
            }

            val toolRegistry = ToolRegistry {
                tool(TestGetWeatherTool)
            }

            runAgentWithStrategy(
                strategy = strategy,
                promptExecutor = mockExecutor,
                toolRegistry = toolRegistry,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                model = model,
                temperature = temperature,
                spanExporter = mockSpanExporter
            )

            val actualSpans = mockSpanExporter.collectedSpans

            // Execute Tool 1 Spans
            val executeTool1NodeSpan = actualSpans.first { it.name == "node.Execute Tool 1" }
            val executeTool1Span = actualSpans.firstOrNull { spanData ->
                spanData.name == "tool.${TestGetWeatherTool.name}" &&
                    spanData.parentSpanId == executeTool1NodeSpan.spanId
            }

            assertNotNull(executeTool1Span)

            // Execute Tool 2 Spans
            val executeTool2NodeSpan = actualSpans.first { it.name == "node.Execute Tool 2" }
            val executeTool2Span = actualSpans.firstOrNull { spanData ->
                spanData.name == "tool.${TestGetWeatherTool.name}" &&
                    spanData.spanId != executeTool2NodeSpan.spanId
            }

            assertNotNull(executeTool2Span)

            // Assert Execute Tool 1 Span
            val actualExecuteTool1SpanAttributes =
                executeTool1Span.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedExecuteTool1SpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "input.value" to "{\"location\":\"Paris\"}",
                "output.value" to toolResponse1,
            )

            assertEquals(expectedExecuteTool1SpanAttributes.size, actualExecuteTool1SpanAttributes.size)
            assertMapsEqual(expectedExecuteTool1SpanAttributes, actualExecuteTool1SpanAttributes)

            // Assert Execute Tool 2 Span
            val actualExecuteTool2SpanAttributes =
                executeTool2Span.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedExecuteTool2SpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "input.value" to "{\"location\":\"London\"}",
                "output.value" to toolResponse2,
            )

            assertEquals(expectedExecuteTool2SpanAttributes.size, actualExecuteTool2SpanAttributes.size)
            assertMapsEqual(expectedExecuteTool2SpanAttributes, actualExecuteTool2SpanAttributes)
        }
    }

    //region Private Methods

    /**
     * Runs an agent with the given strategy and verifies the spans.
     */
    private suspend fun runAgentWithStrategy(
        strategy: AIAgentStrategy<String, String>,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        promptExecutor: PromptExecutor? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = null,
        spanExporter: SpanExporter? = null,
        verbose: Boolean = true
    ) {
        val agentId = "test-agent-id"
        val promptId = "test-prompt-id"
        val testClock = Clock.System

        OpenTelemetryTestAPI.createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            promptExecutor = promptExecutor,
            model = model,
            clock = testClock,
            temperature = temperature,
            systemPrompt = systemPrompt,
            toolRegistry = toolRegistry,
        ) {
            install(OpenTelemetry.Feature) {
                spanExporter?.let { exporter -> addSpanExporter(exporter) }
                setVerbose(verbose)
                openTelemetryConfigurator()
            }
        }.use { agent ->
            agent.run(userPrompt ?: "User prompt message")
        }
    }

    //endregion Private Methods
}
