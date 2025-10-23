package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.agents.features.opentelemetry.span.CreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.InferenceSpan
import ai.koog.agents.features.opentelemetry.span.InvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.NodeExecuteSpan
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LangfuseSpanAdapterTest {

    @Test
    fun `onBeforeSpanStarted adds trace attributes to invoke span`() {
        val config = OpenTelemetryConfig()
        val traceAttributes = listOf(
            CustomAttribute("langfuse.session.id", "session-123"),
            CustomAttribute("langfuse.environment", "production"),
        )
        val adapter = LangfuseSpanAdapter(traceAttributes, config)

        val provider = MockLLMProvider()
        val model = createTestModel(provider)
        val createAgentSpan = CreateAgentSpan(model, agentId = "agent-id")
        val invokeSpan = InvokeAgentSpan(createAgentSpan, provider, runId = "run-id", agentId = "agent-id")

        adapter.onBeforeSpanStarted(invokeSpan)

        traceAttributes.forEach { attribute ->
            assertEquals(attribute.value, invokeSpan.attributes.requireValue(attribute.key))
        }
    }

    @Test
    fun `onBeforeSpanStarted converts inference span events into prompt attributes`() {
        val config = OpenTelemetryConfig().apply { setVerbose(true) }
        val adapter = LangfuseSpanAdapter(emptyList(), config)

        val provider = MockLLMProvider()
        val inferenceSpan = createInferenceSpan(provider, nodeName = "node-name", promptId = "prompt-id")

        val systemContent = "You are Koog."
        val userContent = "What's the forecast for Paris?"
        val assistantContent = "Checking the weather tool."
        val toolResponseContent = "tool response payload"

        inferenceSpan.addEvent(SystemMessageEvent(provider, Message.System(systemContent, RequestMetaInfo.Empty)))
        inferenceSpan.addEvent(UserMessageEvent(provider, Message.User(userContent, RequestMetaInfo.Empty)))
        inferenceSpan.addEvent(AssistantMessageEvent(provider, Message.Assistant(assistantContent, ResponseMetaInfo.Empty)))
        inferenceSpan.addEvent(ToolMessageEvent(provider, toolCallId = "tool-call-response", content = toolResponseContent))

        val toolCallResponse = Message.Tool.Call(
            id = "tool-call-id",
            tool = "getWeather",
            content = "{\"location\":\"Paris\"}",
            metaInfo = ResponseMetaInfo.Empty,
        )
        val choiceEvent = ChoiceEvent(provider, toolCallResponse, index = 0)
        val expectedToolCallJson = EventBodyFields.ToolCalls(listOf(toolCallResponse)).valueString(true)
        inferenceSpan.addEvent(choiceEvent)

        adapter.onBeforeSpanStarted(inferenceSpan)

        assertTrue(inferenceSpan.events.isEmpty(), "Events should be removed after prompt conversion")

        val attributes = inferenceSpan.attributes

        assertEquals("system", attributes.requireValue("gen_ai.prompt.0.role"))
        assertEquals(systemContent, assertIs<HiddenString>(attributes.requireValue("gen_ai.prompt.0.content")).value)

        assertEquals("user", attributes.requireValue("gen_ai.prompt.1.role"))
        assertEquals(userContent, assertIs<HiddenString>(attributes.requireValue("gen_ai.prompt.1.content")).value)

        assertEquals("assistant", attributes.requireValue("gen_ai.prompt.2.role"))
        assertEquals(assistantContent, assertIs<HiddenString>(attributes.requireValue("gen_ai.prompt.2.content")).value)

        assertEquals("tool", attributes.requireValue("gen_ai.prompt.3.role"))
        assertEquals(toolResponseContent, assertIs<HiddenString>(attributes.requireValue("gen_ai.prompt.3.content")).value)

        assertEquals("tool", attributes.requireValue("gen_ai.prompt.4.role"))
        assertEquals(expectedToolCallJson, attributes.requireValue("gen_ai.prompt.4.content"))
    }

    @Test
    fun `onBeforeSpanFinished converts inference span events into completion attributes`() {
        val config = OpenTelemetryConfig().apply { setVerbose(true) }
        val adapter = LangfuseSpanAdapter(emptyList(), config)

        val provider = MockLLMProvider()
        val inferenceSpan = createInferenceSpan(provider, nodeName = "node-name", promptId = "prompt-id")

        val assistantAnswer = "It's sunny in Rome."
        val assistantEvent = AssistantMessageEvent(
            provider,
            Message.Assistant(
                content = assistantAnswer,
                metaInfo = ResponseMetaInfo.Empty,
                finishReason = "stop",
            )
        )
        inferenceSpan.addEvent(assistantEvent)

        val toolCallResponse = Message.Tool.Call(
            id = "tool-call-id",
            tool = "getWeather",
            content = "{\"location\":\"Rome\"}",
            metaInfo = ResponseMetaInfo.Empty,
        )
        val choiceEvent = ChoiceEvent(provider, toolCallResponse, index = 0)
        val expectedToolCallJson = EventBodyFields.ToolCalls(listOf(toolCallResponse)).valueString(true)
        inferenceSpan.addEvent(choiceEvent)

        adapter.onBeforeSpanFinished(inferenceSpan)

        assertTrue(inferenceSpan.events.isEmpty(), "Events should be removed after completion conversion")

        val attributes = inferenceSpan.attributes

        assertEquals("assistant", attributes.requireValue("gen_ai.completion.0.role"))
        assertEquals(assistantAnswer, assertIs<HiddenString>(attributes.requireValue("gen_ai.completion.0.content")).value)

        assertEquals("assistant", attributes.requireValue("gen_ai.completion.1.role"))
        assertEquals(expectedToolCallJson, attributes.requireValue("gen_ai.completion.1.content"))
        assertEquals(
            SpanAttributes.Response.FinishReasonType.ToolCalls.id,
            attributes.requireValue("gen_ai.completion.1.finish_reason"),
        )
    }

    @Test
    fun `onBeforeSpanStarted adds langgraph metadata to node execute spans`() {
        val config = OpenTelemetryConfig()
        val adapter = LangfuseSpanAdapter(emptyList(), config)

        val provider = MockLLMProvider()
        val model = createTestModel(provider)
        val createAgentSpan = CreateAgentSpan(model, agentId = "agent-id")
        val invokeSpan = InvokeAgentSpan(createAgentSpan, provider, runId = "run-id", agentId = "agent-id")

        val firstNodeInput = "planner input"
        val firstNode = NodeExecuteSpan(invokeSpan, runId = "run-id", nodeName = "planner", nodeInput = firstNodeInput)
        adapter.onBeforeSpanStarted(firstNode)

        val firstStep = assertIs<Int>(firstNode.attributes.requireValue("langfuse.observation.metadata.langgraph_step"))
        assertEquals(0, firstStep)
        assertEquals("planner", firstNode.attributes.requireValue("langfuse.observation.metadata.langgraph_node"))

        val secondNodeInput = "executor input"
        val secondNode = NodeExecuteSpan(invokeSpan, runId = "run-id", nodeName = "executor", nodeInput = secondNodeInput)
        adapter.onBeforeSpanStarted(secondNode)

        val secondStep = assertIs<Int>(secondNode.attributes.requireValue("langfuse.observation.metadata.langgraph_step"))
        assertEquals(1, secondStep)
        assertEquals("executor", secondNode.attributes.requireValue("langfuse.observation.metadata.langgraph_node"))
    }
}

private fun createInferenceSpan(
    provider: MockLLMProvider,
    agentId: String = "agent-id",
    runId: String = "run-id",
    nodeName: String = "node-name",
    nodeInput: String = "node-input",
    promptId: String = "prompt-id",
    temperature: Double = 0.4,
): InferenceSpan {
    val model = createTestModel(provider)
    val createAgentSpan = CreateAgentSpan(model, agentId)
    val invokeSpan = InvokeAgentSpan(createAgentSpan, provider, runId, agentId)
    val nodeSpan = NodeExecuteSpan(invokeSpan, runId, nodeName, nodeInput)
    return InferenceSpan(nodeSpan, provider, runId, model, promptId, temperature)
}

private fun createTestModel(provider: MockLLMProvider): LLModel =
    LLModel(provider, "test-model", emptyList(), contextLength = 8192)

private fun List<Attribute>.requireValue(key: String): Any =
    firstOrNull { it.key == key }?.value ?: error("Expected attribute '$key' to be present")
