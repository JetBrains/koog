package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.event.AssistantMessageEvent
import ai.koog.agents.features.opentelemetry.event.ChoiceEvent
import ai.koog.agents.features.opentelemetry.event.EventBodyFields
import ai.koog.agents.features.opentelemetry.event.SystemMessageEvent
import ai.koog.agents.features.opentelemetry.event.ToolMessageEvent
import ai.koog.agents.features.opentelemetry.event.UserMessageEvent
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockLLMProvider
import ai.koog.agents.features.opentelemetry.mock.MockTracer
import ai.koog.agents.features.opentelemetry.span.GenAIAgentSpan
import ai.koog.agents.features.opentelemetry.span.startCreateAgentSpan
import ai.koog.agents.features.opentelemetry.span.startInferenceSpan
import ai.koog.agents.features.opentelemetry.span.startInvokeAgentSpan
import ai.koog.agents.features.opentelemetry.span.startNodeExecuteSpan
import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
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
        val tracer = MockTracer()

        val createAgentSpanId = "create-agent-span-id"
        val agentId = "test-agent-id"

        val createAgentSpan = startCreateAgentSpan(
            tracer = tracer,
            parentSpan = null,
            id = createAgentSpanId,
            agentId = agentId,
            model = model,
            messages = emptyList()
        )

        val invokeAgentSpanId = "invoke-agent-span-id"
        val runId = "run-id"

        val invokeSpan = startInvokeAgentSpan(
            tracer = tracer,
            parentSpan = createAgentSpan,
            id = invokeAgentSpanId,
            model = model,
            runId = runId,
            agentId = agentId,
            llmParams = LLMParams(),
            messages = emptyList(),
            tools = emptyList()
        )

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
        val inferenceSpan = createInferenceSpan(provider, promptId = "prompt-id", nodeId = "node-name")

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
        val inferenceSpan = createInferenceSpan(provider, promptId = "prompt-id", nodeId = "node-name")

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
            GenAIAttributes.Response.FinishReasonType.ToolCalls.id,
            attributes.requireValue("gen_ai.completion.1.finish_reason"),
        )
    }

    @Test
    fun `onBeforeSpanStarted adds langgraph metadata to node execute spans`() {
        val config = OpenTelemetryConfig()
        val adapter = LangfuseSpanAdapter(emptyList(), config)

        val provider = MockLLMProvider()
        val model = createTestModel(provider)
        val tracer = MockTracer()

        val createAgentSpanId = "create-agent-span-id"
        val agentId = "test-agent-id"

        val createAgentSpan = startCreateAgentSpan(
            tracer = tracer,
            parentSpan = null,
            id = createAgentSpanId,
            agentId = agentId,
            model = model,
            messages = emptyList()
        )

        val invokeSpanId = "invoke-agent-span-id"
        val runId = "run-id"

        val invokeSpan = startInvokeAgentSpan(
            tracer = tracer,
            parentSpan = createAgentSpan,
            id = invokeSpanId,
            model = model,
            runId = runId,
            agentId = agentId,
            llmParams = LLMParams(),
            messages = emptyList(),
            tools = emptyList()
        )

        val firstNodeInput = "planner input"
        val firstNodeSpanId = "planner-node-id"

        val firstNode = startNodeExecuteSpan(
            tracer = tracer,
            parentSpan = invokeSpan,
            id = firstNodeSpanId,
            runId = runId,
            nodeId = "planner-node-id",
            nodeInput = firstNodeInput
        )
        adapter.onBeforeSpanStarted(firstNode)

        val firstStep = assertIs<Int>(firstNode.attributes.requireValue("langfuse.observation.metadata.langgraph_step"))
        assertEquals(0, firstStep)

        // Langfuse span adapter adds an attribute 'langgraph_node' with the node name as a value.
        assertEquals("planner-node-id", firstNode.attributes.requireValue("langfuse.observation.metadata.langgraph_node"))

        val secondNodeInput = "executor input"
        val secondNodeSpanId = "executor-node-id"

        val secondNode = startNodeExecuteSpan(
            tracer = tracer,
            parentSpan = firstNode,
            id = secondNodeSpanId,
            runId = runId,
            nodeId = secondNodeSpanId,
            nodeInput = secondNodeInput
        )

        adapter.onBeforeSpanStarted(secondNode)

        val secondStep = assertIs<Int>(secondNode.attributes.requireValue("langfuse.observation.metadata.langgraph_step"))
        assertEquals(1, secondStep)

        // Langfuse span adapter adds an attribute 'langgraph_node' with the node name as a value.
        assertEquals("executor-node-id", secondNode.attributes.requireValue("langfuse.observation.metadata.langgraph_node"))
    }

    private fun createInferenceSpan(
        provider: MockLLMProvider,
        agentId: String = "agent-id",
        runId: String = "run-id",
        nodeInput: String = "node-input",
        nodeId: String = "node-id",
        promptId: String = "prompt-id",
        temperature: Double = 0.4,
    ): GenAIAgentSpan {
        val model = createTestModel(provider)
        val tracer = MockTracer()

        val createAgentSpanId = "create-agent-span-id"
        val createAgentSpan = startCreateAgentSpan(
            tracer = tracer,
            parentSpan = null,
            id = createAgentSpanId,
            agentId = agentId,
            model = model,
            messages = emptyList()
        )

        val invokeSpanId = "invoke-agent-span-id"
        val invokeSpan = startInvokeAgentSpan(
            tracer = tracer,
            parentSpan = createAgentSpan,
            id = invokeSpanId,
            model = model,
            runId = runId,
            agentId = agentId,
            llmParams = LLMParams(),
            messages = emptyList(),
            tools = emptyList()
        )

        val nodeSpanId = "node-span-id"
        val nodeSpan = startNodeExecuteSpan(
            tracer = tracer,
            parentSpan = invokeSpan,
            id = nodeSpanId,
            runId = runId,
            nodeId = nodeId,
            nodeInput = nodeInput
        )

        val inferenceSpanId = "inference-span-id"
        val inferenceSpan = startInferenceSpan(
            tracer = tracer,
            parentSpan = nodeSpan,
            id = inferenceSpanId,
            provider = provider,
            runId = runId,
            model = model,
            messages = emptyList(),
            llmParams = LLMParams(temperature = temperature),
            tools = emptyList()
        )

        return inferenceSpan
    }

    private fun createTestModel(provider: MockLLMProvider): LLModel =
        LLModel(provider, "test-model", emptyList(), contextLength = 8192)

    private fun List<Attribute>.requireValue(key: String): Any =
        firstOrNull { it.key == key }?.value ?: error("Expected attribute '$key' to be present")
}
