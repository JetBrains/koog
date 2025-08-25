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
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
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
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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

            val spansRun = actualSpans.filter { it.name.startsWith("run.") }
            assertEquals(1, spansRun.size)

            val spansStartNode = actualSpans.filter { it.name == "node.__start__" }
            assertEquals(1, spansStartNode.size)

            val spansLLMCall = actualSpans.filter { it.name == "node.llm-call" }
            assertEquals(1, spansLLMCall.size)

            val spansLLMGeneration = actualSpans.filter { it.name == "llm.test-prompt-id" }
            assertEquals(1, spansLLMGeneration.size)

            val spanRunNode = spansRun.first()
            val spanStartNode = spansStartNode.first()
            val spanLLMNode = spansLLMCall.first()
            val spanLLMGeneration = spansLLMGeneration.first()

            assertEquals(spanStartNode.parentSpanId, spanRunNode.spanId)
            assertEquals(spanLLMNode.parentSpanId, spanRunNode.spanId)
            assertEquals(spanLLMGeneration.parentSpanId, spanLLMNode.spanId)

            val actualSpanAttributes = spanLLMGeneration.attributes.asMap()
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
                "gen_ai.response.finish_reasons" to listOf(SpanAttributes.Response.FinishReasonType.Stop.id),

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

    abstract fun testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(model: LLModel, temperature: Double, systemPrompt: String, userPrompt: String, runId: String, toolCallId: String): Map<String, Any>

    abstract fun testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(model: LLModel, temperature: Double, systemPrompt: String, userPrompt: String, runId: String, toolCallId: String, toolResponse: String, finalResponse: String): Map<String, Any>

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

            val toolCallId = "get-weather-tool-call-id"

            val mockExecutor = getMockExecutor {
                mockLLMToolCall(tool = TestGetWeatherTool, args = toolCallArgs, toolCallId = toolCallId) onRequestEquals userPrompt
                mockLLMAnswer(response = finalResponse) onRequestContains toolResponse
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

            // Assert collected spans
            val actualSpans = mockSpanExporter.collectedSpans

            val toolSpans = actualSpans.filter { it.name == "tool.Get whether" }
            assertEquals(1, toolSpans.size)

            val runNode = actualSpans.firstOrNull { it.name.startsWith("run.") }
            assertNotNull(runNode)

            val startNode = actualSpans.firstOrNull { it.name == "node.__start__" }
            assertNotNull(startNode)

            val llmRequestNode = actualSpans.firstOrNull { it.name == "node.LLM Request" }
            assertNotNull(llmRequestNode)

            val executeToolNode = actualSpans.firstOrNull { it.name == "node.Execute Tool" }
            assertNotNull(executeToolNode)

            val sendToolResultNode = actualSpans.firstOrNull { it.name == "node.Send Tool Result" }
            assertNotNull(sendToolResultNode)

            val toolCallSpan = actualSpans.firstOrNull { it.name == "tool.Get whether" }
            assertNotNull(toolCallSpan)

            // All nodes should have runNode as parent
            assertEquals(startNode.parentSpanId, runNode.spanId)
            assertEquals(llmRequestNode.parentSpanId, runNode.spanId)
            assertEquals(executeToolNode.parentSpanId, runNode.spanId)
            assertEquals(sendToolResultNode.parentSpanId, runNode.spanId)

            // Check LLM Call span with the initial call and tool call request
            val llmSpans = actualSpans.filter { it.name == "llm.test-prompt-id" }
            val actualInitialLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == llmRequestNode.spanId }
            assertNotNull(actualInitialLLMCallSpan)

            val actualInitialLLMCallSpanAttributes =
                actualInitialLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedInitialLLMCallSpansAttributes = testLLMCallToolCallLLMCallGetExpectedInitialLLMCallSpanAttributes(
                model = model,
                temperature = temperature,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                runId = mockSpanExporter.lastRunId,
                toolCallId = toolCallId,
            )

            assertEquals(expectedInitialLLMCallSpansAttributes.size, actualInitialLLMCallSpanAttributes.size)
            assertMapsEqual(expectedInitialLLMCallSpansAttributes, actualInitialLLMCallSpanAttributes)

            // Check LLM Call span with the final LLM response after the tool is executed
            val actualFinalLLMCallSpan = llmSpans.firstOrNull { it.parentSpanId == sendToolResultNode.spanId }
            assertNotNull(actualFinalLLMCallSpan)

            val actualFinalLLMCallSpanAttributes =
                actualFinalLLMCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedFinalLLMCallSpansAttributes = testLLMCallToolCallLLMCallGetExpectedFinalLLMCallSpansAttributes(
                model = model,
                temperature = temperature,
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                runId = mockSpanExporter.lastRunId,
                toolCallId = toolCallId,
                toolResponse = toolResponse,
                finalResponse = finalResponse,
            )

            assertEquals(expectedFinalLLMCallSpansAttributes.size, actualFinalLLMCallSpanAttributes.size)
            assertMapsEqual(expectedFinalLLMCallSpansAttributes, actualFinalLLMCallSpanAttributes)

            // Tool span should have executed tool node as parent
            assertEquals(executeToolNode.spanId, toolCallSpan.parentSpanId)
            val actualToolCallSpanAttributes =
                toolCallSpan.attributes.asMap().map { (key, value) -> key.key to value }.toMap()

            val expectedToolCallSpanAttributes = mapOf(
                "gen_ai.tool.name" to TestGetWeatherTool.name,
                "gen_ai.tool.description" to TestGetWeatherTool.descriptor.description,
                "gen_ai.tool.call.id" to toolCallId,
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
                edge(sendToolResult1 forwardTo nodeFinish onAssistantMessage { true })
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
                mockLLMToolCall(tool = TestGetWeatherTool, args = toolCallArgs1) onRequestEquals userPrompt
                mockLLMToolCall(tool = TestGetWeatherTool, args = toolCallArgs2) onRequestContains toolResponse1
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
}
