package ai.koog.agents.features.opentelemetry.integration.langfuse

import ai.koog.agents.features.opentelemetry.attribute.Attribute
import ai.koog.agents.features.opentelemetry.attribute.CustomAttribute
import ai.koog.agents.features.opentelemetry.attribute.GenAIAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockContextFactory
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
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LangfuseSpanAdapterTest {

    /** Shapes of a signature-only Reasoning part, as each provider client builds it. */
    enum class SignatureOnlyReasoning(val content: List<String>) {
        GOOGLE(emptyList()),
        ANTHROPIC(listOf("")),
    }

    @Test
    fun `onBeforeSpanStarted adds trace attributes to invoke span`() {
        val traceAttributes = listOf(
            CustomAttribute("langfuse.session.id", "session-123"),
            CustomAttribute("langfuse.environment", "production"),
        )
        val adapter = LangfuseSpanAdapter(traceAttributes, OpenTelemetryConfig())

        val provider = MockLLMProvider()
        val model = createTestModel(provider)
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val createAgentSpanId = "create-agent-span-id"
        val agentId = "test-agent-id"

        val createAgentSpan = startCreateAgentSpan(
            tracer = tracer,
            contextFactory = contextFactory,
            parentSpan = null,
            id = createAgentSpanId,
            agentId = agentId,
            model = model,
            messages = emptyList()
        )

        adapter.onBeforeSpanStarted(createAgentSpan)

        traceAttributes.forEach { attribute ->
            assertEquals(attribute.value, createAgentSpan.attributes.requireValue(attribute.key))
        }

        val invokeAgentSpanId = "invoke-agent-span-id"
        val runId = "run-id"

        val invokeSpan = startInvokeAgentSpan(
            tracer = tracer,
            contextFactory = contextFactory,
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
    fun `onBeforeSpanStarted converts inference input messages into prompt attributes`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val provider = MockLLMProvider()

        val systemContent = "You are Koog."
        val userContent = "What's the forecast for Paris?"
        val assistantContent = "Checking the weather tool."
        val toolResponseContent = "tool response payload"

        val toolCallPart = MessagePart.Tool.Call(id = "tool-call-id", tool = "getWeather", args = "{\"location\":\"Paris\"}")
        val inputMessages = listOf(
            Message.System(systemContent, RequestMetaInfo.Empty),
            Message.User(userContent, RequestMetaInfo.Empty),
            Message.Assistant(assistantContent, ResponseMetaInfo.Empty),
            Message.User(
                parts = listOf(MessagePart.Tool.Result(id = "tool-call-id", tool = "getWeather", output = toolResponseContent)),
                metaInfo = RequestMetaInfo.Empty,
            ),
            Message.Assistant(
                parts = listOf(toolCallPart),
                metaInfo = ResponseMetaInfo.Empty,
                finishReason = "tool_call",
            ),
        )
        val inferenceSpan = createInferenceSpan(provider, messages = inputMessages)

        adapter.onBeforeSpanStarted(inferenceSpan)

        val attributes = inferenceSpan.attributes

        // Adapter decomposes Input.Messages into per-index prompt attributes but leaves the
        // original OTel-standard attribute in place.
        assertEquals(1, attributes.count { it is GenAIAttributes.Input.Messages })

        assertEquals("system", attributes.requireValue("gen_ai.prompt.0.role"))
        val actualSystemContent = attributes.requireValue("gen_ai.prompt.0.content")
        assertIs<HiddenString>(actualSystemContent)
        assertEquals(systemContent, actualSystemContent.value)

        assertEquals("user", attributes.requireValue("gen_ai.prompt.1.role"))
        val actualUserContent = attributes.requireValue("gen_ai.prompt.1.content")
        assertIs<HiddenString>(actualUserContent)
        assertEquals(userContent, actualUserContent.value)

        assertEquals("assistant", attributes.requireValue("gen_ai.prompt.2.role"))
        val actualAssistantContent = attributes.requireValue("gen_ai.prompt.2.content")
        assertIs<HiddenString>(actualAssistantContent)
        assertEquals(assistantContent, actualAssistantContent.value)

        assertEquals("tool", attributes.requireValue("gen_ai.prompt.3.role"))
        val actualToolResponseContent = attributes.requireValue("gen_ai.prompt.3.content")
        assertIs<HiddenString>(actualToolResponseContent)
        assertEquals(toolResponseContent, actualToolResponseContent.value)

        assertEquals("assistant", attributes.requireValue("gen_ai.prompt.4.role"))
        assertToolCall(attributes, "gen_ai.prompt.4.tool_calls.0", toolCallPart)
        assertNull(attributes.valueOf("gen_ai.prompt.4.content"))
    }

    @Test
    fun `onBeforeSpanFinished converts inference output messages into completion attributes`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val provider = MockLLMProvider()
        val inferenceSpan = createInferenceSpan(provider)

        val assistantAnswer = "It's sunny in Rome."
        val assistantMessage = Message.Assistant(
            content = assistantAnswer,
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "stop",
        )

        val toolCallPart2 = MessagePart.Tool.Call(id = "tool-call-id", tool = "getWeather", args = "{\"location\":\"Rome\"}")
        val toolCallMessage2 = Message.Assistant(
            parts = listOf(toolCallPart2),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "tool_call",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage, toolCallMessage2)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val attributes = inferenceSpan.attributes

        // Adapter decomposes Output.Messages into per-index completion attributes but leaves
        // the original OTel-standard attribute in place.
        assertEquals(1, attributes.count { it is GenAIAttributes.Output.Messages })

        assertEquals("assistant", attributes.requireValue("gen_ai.completion.0.role"))
        val actualAssistantAnswer = attributes.requireValue("gen_ai.completion.0.content")
        assertIs<HiddenString>(actualAssistantAnswer)
        assertEquals(assistantAnswer, actualAssistantAnswer.value)
        assertEquals("stop", attributes.requireValue("gen_ai.completion.0.finish_reason"))

        assertEquals("assistant", attributes.requireValue("gen_ai.completion.1.role"))
        assertToolCall(attributes, "gen_ai.completion.1.tool_calls.0", toolCallPart2)
        assertNull(attributes.valueOf("gen_ai.completion.1.content"))
        assertEquals(
            GenAIAttributes.Response.FinishReasonType.ToolCalls.id,
            attributes.requireValue("gen_ai.completion.1.finish_reason"),
        )
    }

    @ParameterizedTest
    @EnumSource(SignatureOnlyReasoning::class)
    fun testPromptUsesTextWhenReasoningIsSignatureOnly(reasoning: SignatureOnlyReasoning) {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val provider = MockLLMProvider()
        val assistantAnswer = "It's sunny in Rome."
        val inputMessages = listOf(
            Message.User("What's the forecast for Rome?", RequestMetaInfo.Empty),
            Message.Assistant(
                parts = listOf(
                    MessagePart.Reasoning(content = reasoning.content, encrypted = "signature"),
                    MessagePart.Text(assistantAnswer),
                ),
                metaInfo = ResponseMetaInfo.Empty,
                finishReason = "stop",
            ),
        )
        val inferenceSpan = createInferenceSpan(provider, messages = inputMessages)

        adapter.onBeforeSpanStarted(inferenceSpan)

        val actualContent = inferenceSpan.attributes.requireValue("gen_ai.prompt.1.content")
        assertIs<HiddenString>(actualContent)
        assertEquals(assistantAnswer, actualContent.value)
    }

    @ParameterizedTest
    @EnumSource(SignatureOnlyReasoning::class)
    fun testCompletionUsesTextWhenReasoningIsSignatureOnly(reasoning: SignatureOnlyReasoning) {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val provider = MockLLMProvider()
        val inferenceSpan = createInferenceSpan(provider)

        val assistantAnswer = "It's sunny in Rome."
        val assistantMessage = Message.Assistant(
            parts = listOf(
                MessagePart.Reasoning(content = reasoning.content, encrypted = "signature"),
                MessagePart.Text(assistantAnswer),
            ),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "stop",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val attributes = inferenceSpan.attributes

        val actualContent = attributes.requireValue("gen_ai.completion.0.content")
        assertIs<HiddenString>(actualContent)
        assertEquals(assistantAnswer, actualContent.value)
        assertEquals("stop", attributes.requireValue("gen_ai.completion.0.finish_reason"))
    }

    @Test
    fun `onBeforeSpanFinished keeps answer text next to reasoning that carries text`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())
        val inferenceSpan = createInferenceSpan(MockLLMProvider())

        // Claude with extended thinking, or Gemini with includeThoughts, returns readable reasoning next to the answer.
        val assistantMessage = Message.Assistant(
            parts = listOf(
                MessagePart.Reasoning(content = listOf("Dan is at seat 1, so Cara is at seat 2."), encrypted = "signature"),
                MessagePart.Text("Eva"),
            ),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "end_turn",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val attributes = inferenceSpan.attributes
        assertEquals("reasoning", attributes.requireValue("gen_ai.completion.0.content.0.type"))
        assertHidden("Dan is at seat 1, so Cara is at seat 2.", attributes.requireValue("gen_ai.completion.0.content.0.text"))
        assertEquals("text", attributes.requireValue("gen_ai.completion.0.content.1.type"))
        assertHidden("Eva", attributes.requireValue("gen_ai.completion.0.content.1.text"))
        assertNull(attributes.valueOf("gen_ai.completion.0.content"))
        assertEquals("end_turn", attributes.requireValue("gen_ai.completion.0.finish_reason"))
    }

    @Test
    fun `onBeforeSpanFinished keeps text written next to a tool call`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())
        val inferenceSpan = createInferenceSpan(MockLLMProvider())

        val toolCall = MessagePart.Tool.Call(id = "toolu_1", tool = "get_weather", args = "{\"city\":\"Prague\"}")
        val assistantMessage = Message.Assistant(
            parts = listOf(MessagePart.Text("I will check the weather in Prague."), toolCall),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "tool_use",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val attributes = inferenceSpan.attributes
        assertHidden("I will check the weather in Prague.", attributes.requireValue("gen_ai.completion.0.content"))
        assertToolCall(attributes, "gen_ai.completion.0.tool_calls.0", toolCall)
        assertEquals(
            GenAIAttributes.Response.FinishReasonType.ToolCalls.id,
            attributes.requireValue("gen_ai.completion.0.finish_reason"),
        )
    }

    @Test
    fun `onBeforeSpanFinished writes reasoning when the response was cut before any text`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())
        val inferenceSpan = createInferenceSpan(MockLLMProvider())

        val assistantMessage = Message.Assistant(
            parts = listOf(MessagePart.Reasoning(content = listOf("Let me check every order."), encrypted = "signature")),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "max_tokens",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val attributes = inferenceSpan.attributes
        assertEquals("reasoning", attributes.requireValue("gen_ai.completion.0.content.0.type"))
        assertHidden("Let me check every order.", attributes.requireValue("gen_ai.completion.0.content.0.text"))
        assertNull(attributes.valueOf("gen_ai.completion.0.content.1.type"))
        assertEquals("max_tokens", attributes.requireValue("gen_ai.completion.0.finish_reason"))
    }

    @Test
    fun `onBeforeSpanStarted writes one tool message per result of parallel tool calls`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val pragueCall = MessagePart.Tool.Call(id = "call-prague", tool = "get_weather", args = "{\"city\":\"Prague\"}")
        val brnoCall = MessagePart.Tool.Call(id = "call-brno", tool = "get_weather", args = "{\"city\":\"Brno\"}")
        val inputMessages = listOf(
            Message.System("You are Koog.", RequestMetaInfo.Empty),
            Message.User("Weather in Prague and Brno?", RequestMetaInfo.Empty),
            Message.Assistant(
                parts = listOf(MessagePart.Text("I will check both cities."), pragueCall, brnoCall),
                metaInfo = ResponseMetaInfo.Empty,
                finishReason = "tool_use",
            ),
            Message.User(
                parts = listOf(
                    MessagePart.Tool.Result(id = "call-prague", tool = "get_weather", output = "Prague: 18 C"),
                    MessagePart.Tool.Result(id = "call-brno", tool = "get_weather", output = "Brno: 20 C"),
                ),
                metaInfo = RequestMetaInfo.Empty,
            ),
            Message.User("Thanks.", RequestMetaInfo.Empty),
        )
        val inferenceSpan = createInferenceSpan(MockLLMProvider(), messages = inputMessages)

        adapter.onBeforeSpanStarted(inferenceSpan)

        val attributes = inferenceSpan.attributes
        assertEquals("assistant", attributes.requireValue("gen_ai.prompt.2.role"))
        assertHidden("I will check both cities.", attributes.requireValue("gen_ai.prompt.2.content"))
        assertToolCall(attributes, "gen_ai.prompt.2.tool_calls.0", pragueCall)
        assertToolCall(attributes, "gen_ai.prompt.2.tool_calls.1", brnoCall)

        assertEquals("tool", attributes.requireValue("gen_ai.prompt.3.role"))
        assertEquals("call-prague", attributes.requireValue("gen_ai.prompt.3.tool_call_id"))
        assertHidden("Prague: 18 C", attributes.requireValue("gen_ai.prompt.3.content"))
        assertEquals("tool", attributes.requireValue("gen_ai.prompt.4.role"))
        assertEquals("call-brno", attributes.requireValue("gen_ai.prompt.4.tool_call_id"))
        assertHidden("Brno: 20 C", attributes.requireValue("gen_ai.prompt.4.content"))

        assertEquals("user", attributes.requireValue("gen_ai.prompt.5.role"))
        assertHidden("Thanks.", attributes.requireValue("gen_ai.prompt.5.content"))
    }

    @Test
    fun `onBeforeSpanStarted adds langgraph metadata to node execute spans`() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val provider = MockLLMProvider()
        val model = createTestModel(provider)
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val createAgentSpanId = "create-agent-span-id"
        val agentId = "test-agent-id"

        val createAgentSpan = startCreateAgentSpan(
            tracer = tracer,
            contextFactory = contextFactory,
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
            contextFactory = contextFactory,
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
            contextFactory = contextFactory,
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
            contextFactory = contextFactory,
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
        temperature: Double = 0.4,
        messages: List<Message> = emptyList(),
    ): GenAIAgentSpan {
        val model = createTestModel(provider)
        val tracer = MockTracer()
        val contextFactory = MockContextFactory()

        val createAgentSpanId = "create-agent-span-id"
        val createAgentSpan = startCreateAgentSpan(
            tracer = tracer,
            contextFactory = contextFactory,
            parentSpan = null,
            id = createAgentSpanId,
            agentId = agentId,
            model = model,
            messages = emptyList()
        )

        val invokeSpanId = "invoke-agent-span-id"
        val invokeSpan = startInvokeAgentSpan(
            tracer = tracer,
            contextFactory = contextFactory,
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
            contextFactory = contextFactory,
            parentSpan = invokeSpan,
            id = nodeSpanId,
            runId = runId,
            nodeId = nodeId,
            nodeInput = nodeInput
        )

        val inferenceSpanId = "inference-span-id"
        val inferenceSpan = startInferenceSpan(
            tracer = tracer,
            contextFactory = contextFactory,
            parentSpan = nodeSpan,
            id = inferenceSpanId,
            provider = provider,
            runId = runId,
            model = model,
            messages = messages,
            llmParams = LLMParams(temperature = temperature),
            tools = emptyList()
        )

        return inferenceSpan
    }

    private fun createTestModel(provider: MockLLMProvider): LLModel =
        LLModel(provider, "test-model", emptyList(), contextLength = 8192)

    private fun List<Attribute>.requireValue(key: String): Any =
        valueOf(key) ?: error("Expected attribute '$key' to be present")

    private fun List<Attribute>.valueOf(key: String): Any? = firstOrNull { it.key == key }?.value

    private fun assertHidden(expected: String, actual: Any) {
        assertEquals(expected, assertIs<HiddenString>(actual).value)
    }

    private fun assertToolCall(attributes: List<Attribute>, prefix: String, call: MessagePart.Tool.Call) {
        assertEquals<Any?>(call.id, attributes.requireValue("$prefix.id"))
        assertEquals("function", attributes.requireValue("$prefix.type"))
        assertEquals(call.tool, attributes.requireValue("$prefix.function.name"))
        assertHidden(call.args, attributes.requireValue("$prefix.function.arguments"))
    }
}
