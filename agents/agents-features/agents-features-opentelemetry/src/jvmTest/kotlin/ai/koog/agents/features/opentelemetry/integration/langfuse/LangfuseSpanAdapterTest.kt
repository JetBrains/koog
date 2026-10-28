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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LangfuseSpanAdapterTest {

    /** Shapes of a signature-only Reasoning part, as each provider client builds it. */
    enum class SignatureOnlyReasoning(val content: List<String>) {
        GOOGLE(emptyList()),
        ANTHROPIC(listOf("")),
    }

    /**
     * Token counts a response reports in its metadata, with the usage details Langfuse should receive.
     * Anthropic reports its cache reads under `cacheReadInputTokens` and already leaves them out of the input count.
     */
    enum class TokenDetails(
        val cached: Int?,
        val reasoning: Int?,
        val expectedUsageDetails: String?,
        val anthropicCacheRead: Int? = null,
    ) {
        CACHED_AND_REASONING(800, 200, """{"input": 200, "input_cached_tokens": 800, "output": 50, "output_reasoning": 200}"""),
        REASONING_ONLY(null, 200, """{"input": 1000, "output": 50, "output_reasoning": 200}"""),
        CACHED_ONLY(800, null, """{"input": 200, "input_cached_tokens": 800, "output": 50}"""),
        ANTHROPIC_CACHE_READ_ONLY(null, null, null, anthropicCacheRead = 300),
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

        val inputMessages = listOf(
            Message.System(systemContent, RequestMetaInfo.Empty),
            Message.User(userContent, RequestMetaInfo.Empty),
            Message.Assistant(assistantContent, ResponseMetaInfo.Empty),
            Message.User(
                parts = listOf(MessagePart.Tool.Result(id = "tool-call-id", tool = "getWeather", output = toolResponseContent)),
                metaInfo = RequestMetaInfo.Empty,
            ),
            Message.Assistant(
                parts = listOf(MessagePart.Tool.Call(id = "tool-call-id", tool = "getWeather", args = "{\"location\":\"Paris\"}")),
                metaInfo = ResponseMetaInfo.Empty,
                finishReason = "tool_call",
            ),
        )
        val inferenceSpan = createInferenceSpan(provider, messages = inputMessages)

        adapter.onBeforeSpanStarted(inferenceSpan)

        val attributes = inferenceSpan.attributes

        // Adapter rewrites Input.Messages into the prompt attribute but leaves the
        // original OTel-standard attribute in place.
        assertEquals(1, attributes.count { it is GenAIAttributes.Input.Messages })

        assertJsonAttribute(
            """
            [
              {"role": "system", "content": "$systemContent"},
              {"role": "user", "content": "$userContent"},
              {"role": "assistant", "content": "$assistantContent"},
              {"role": "tool", "tool_call_id": "tool-call-id", "content": "$toolResponseContent"},
              {"role": "assistant", "tool_calls": [
                {"id": "tool-call-id", "type": "function", "function": {"name": "getWeather", "arguments": "{\"location\":\"Paris\"}"}}
              ]}
            ]
            """,
            attributes,
            "gen_ai.prompt",
        )
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

        val toolCallMessage2 = Message.Assistant(
            parts = listOf(MessagePart.Tool.Call(id = "tool-call-id", tool = "getWeather", args = "{\"location\":\"Rome\"}")),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "tool_call",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage, toolCallMessage2)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val attributes = inferenceSpan.attributes

        // Adapter rewrites Output.Messages into the completion attribute but leaves
        // the original OTel-standard attribute in place.
        assertEquals(1, attributes.count { it is GenAIAttributes.Output.Messages })

        assertJsonAttribute(
            """
            [
              {"role": "assistant", "content": "$assistantAnswer", "finish_reason": "stop"},
              {"role": "assistant", "tool_calls": [
                {"id": "tool-call-id", "type": "function", "function": {"name": "getWeather", "arguments": "{\"location\":\"Rome\"}"}}
              ], "finish_reason": "tool_calls"}
            ]
            """,
            attributes,
            "gen_ai.completion",
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

        assertJsonAttribute(
            """
            [
              {"role": "user", "content": "What's the forecast for Rome?"},
              {"role": "assistant", "content": "$assistantAnswer"}
            ]
            """,
            inferenceSpan.attributes,
            "gen_ai.prompt",
        )
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

        assertJsonAttribute(
            """[{"role": "assistant", "content": "$assistantAnswer", "finish_reason": "stop"}]""",
            inferenceSpan.attributes,
            "gen_ai.completion",
        )
    }

    @Test
    fun testCompletionKeepsTextNextToReadableReasoning() {
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

        assertJsonAttribute(
            """
            [
              {"role": "assistant", "content": [
                {"type": "reasoning", "text": "Dan is at seat 1, so Cara is at seat 2."},
                {"type": "text", "text": "Eva"}
              ], "finish_reason": "end_turn"}
            ]
            """,
            inferenceSpan.attributes,
            "gen_ai.completion",
        )
    }

    @Test
    fun testCompletionKeepsTextNextToToolCall() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())
        val inferenceSpan = createInferenceSpan(MockLLMProvider())

        val assistantMessage = Message.Assistant(
            parts = listOf(
                MessagePart.Text("I will check the weather in Prague."),
                MessagePart.Tool.Call(id = "toolu_1", tool = "get_weather", args = "{\"city\":\"Prague\"}"),
            ),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "tool_use",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        assertJsonAttribute(
            """
            [
              {"role": "assistant", "content": "I will check the weather in Prague.", "tool_calls": [
                {"id": "toolu_1", "type": "function", "function": {"name": "get_weather", "arguments": "{\"city\":\"Prague\"}"}}
              ], "finish_reason": "tool_calls"}
            ]
            """,
            inferenceSpan.attributes,
            "gen_ai.completion",
        )
    }

    @Test
    fun testCompletionWritesReasoningWhenResponseIsCutBeforeText() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())
        val inferenceSpan = createInferenceSpan(MockLLMProvider())

        val assistantMessage = Message.Assistant(
            parts = listOf(MessagePart.Reasoning(content = listOf("Let me check every order."), encrypted = "signature")),
            metaInfo = ResponseMetaInfo.Empty,
            finishReason = "max_tokens",
        )
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(assistantMessage)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        assertJsonAttribute(
            """
            [
              {"role": "assistant", "content": [{"type": "reasoning", "text": "Let me check every order."}], "finish_reason": "max_tokens"}
            ]
            """,
            inferenceSpan.attributes,
            "gen_ai.completion",
        )
    }

    @Test
    fun testPromptWritesOneToolMessagePerParallelToolResult() {
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

        assertJsonAttribute(
            """
            [
              {"role": "system", "content": "You are Koog."},
              {"role": "user", "content": "Weather in Prague and Brno?"},
              {"role": "assistant", "content": "I will check both cities.", "tool_calls": [
                {"id": "call-prague", "type": "function", "function": {"name": "get_weather", "arguments": "{\"city\":\"Prague\"}"}},
                {"id": "call-brno", "type": "function", "function": {"name": "get_weather", "arguments": "{\"city\":\"Brno\"}"}}
              ]},
              {"role": "tool", "tool_call_id": "call-prague", "content": "Prague: 18 C"},
              {"role": "tool", "tool_call_id": "call-brno", "content": "Brno: 20 C"},
              {"role": "user", "content": "Thanks."}
            ]
            """,
            inferenceSpan.attributes,
            "gen_ai.prompt",
        )
    }

    @Test
    fun testPromptWritesLongToolCallHistoryAsOneAttribute() {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())

        val toolRounds = (1..30).flatMap { round ->
            listOf(
                Message.Assistant(
                    parts = listOf(MessagePart.Tool.Call(id = "call-$round", tool = "get_weather", args = "{}")),
                    metaInfo = ResponseMetaInfo.Empty,
                    finishReason = "tool_use",
                ),
                Message.User(
                    parts = listOf(MessagePart.Tool.Result(id = "call-$round", tool = "get_weather", output = "18 C")),
                    metaInfo = RequestMetaInfo.Empty,
                ),
            )
        }
        val inputMessages = listOf(Message.User("Weather?", RequestMetaInfo.Empty)) + toolRounds
        val inferenceSpan = createInferenceSpan(MockLLMProvider(), messages = inputMessages)
        val attributeCountBefore = inferenceSpan.attributes.size

        adapter.onBeforeSpanStarted(inferenceSpan)

        // The OTel SDK keeps 128 attributes per span by default and drops the rest.
        assertEquals(attributeCountBefore + 1, inferenceSpan.attributes.size)
        val prompt = assertIs<HiddenString>(inferenceSpan.attributes.requireValue("gen_ai.prompt")).value
        assertEquals(61, Json.parseToJsonElement(prompt).jsonArray.size)
    }

    @ParameterizedTest
    @EnumSource(TokenDetails::class)
    fun testInferenceSpanReportsCachedAndReasoningTokensAsUsageDetails(details: TokenDetails) {
        val adapter = LangfuseSpanAdapter(emptyList(), OpenTelemetryConfig())
        val inferenceSpan = createInferenceSpan(MockLLMProvider())
        val answer = assistantWithUsage(1000, 50, details.cached, details.reasoning, details.anthropicCacheRead)
        inferenceSpan.addAttribute(GenAIAttributes.Output.Messages(listOf(answer)))

        adapter.onBeforeSpanFinished(inferenceSpan)

        val usageDetails = inferenceSpan.attributes.firstOrNull { it.key == "langfuse.observation.usage_details" }?.value as String?
        assertEquals(details.expectedUsageDetails?.let(Json::parseToJsonElement), usageDetails?.let(Json::parseToJsonElement))
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

    private fun assistantWithUsage(inputTokens: Int, outputTokens: Int, cached: Int?, reasoning: Int?, anthropicCacheRead: Int?) =
        Message.Assistant(
            content = "answer",
            metaInfo = ResponseMetaInfo.Empty.copy(
                inputTokensCount = inputTokens,
                outputTokensCount = outputTokens,
                metadata = buildJsonObject {
                    cached?.let { put("cachedContentTokenCount", it) }
                    reasoning?.let { put("thoughtsTokenCount", it) }
                    anthropicCacheRead?.let { put("cacheReadInputTokens", it) }
                }.takeIf { it.isNotEmpty() },
            ),
            finishReason = "stop",
        )

    private fun createTestModel(provider: MockLLMProvider): LLModel =
        LLModel(provider, "test-model", emptyList(), contextLength = 8192)

    private fun List<Attribute>.requireValue(key: String): Any =
        firstOrNull { it.key == key }?.value ?: error("Expected attribute '$key' to be present")

    private fun assertJsonAttribute(expectedJson: String, attributes: List<Attribute>, key: String) {
        val actual = assertIs<HiddenString>(attributes.requireValue(key)).value
        assertEquals(Json.parseToJsonElement(expectedJson), Json.parseToJsonElement(actual))
    }
}
