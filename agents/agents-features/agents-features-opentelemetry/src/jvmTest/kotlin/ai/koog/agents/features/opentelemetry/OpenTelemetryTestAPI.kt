package ai.koog.agents.features.opentelemetry

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentFunctionalStrategy
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_AGENT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_PROMPT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.MOCK_LLM_RESPONSE_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.TEMPERATURE
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleLLMCallStrategy
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Strategy.getSingleToolCallStrategy
import ai.koog.agents.features.opentelemetry.attribute.SpanAttributes
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetryConfig
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal object OpenTelemetryTestAPI {
    private val serializer = KotlinxSerializer()

    internal val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    private val spansCollectionTimeout = 5.seconds

    internal object Parameter {
        internal const val DEFAULT_AGENT_ID = "test-agent-id"
        internal const val DEFAULT_PROMPT_ID = "test-prompt-id"
        internal const val DEFAULT_STRATEGY_NAME = "test-strategy"
        internal val defaultModel = OpenAIModels.Chat.GPT4o

        internal const val SYSTEM_PROMPT = "You are the application that predicts weather"

        internal const val USER_PROMPT_PARIS = "What's the weather in Paris?"
        internal const val MOCK_LLM_RESPONSE_PARIS = "The weather in Paris is rainy and overcast, with temperatures around 57°F"

        internal const val USER_PROMPT_LONDON = "What's the weather in London?"
        internal const val MOCK_LLM_RESPONSE_LONDON = "The weather in London is sunny, with temperatures around 65°F"

        internal const val TEMPERATURE: Double = 0.4
    }

    internal data class MockToolCallResponse<TArgs, TResult>(
        val tool: Tool<TArgs, TResult>,
        val arguments: TArgs,
        val toolResult: TResult,
        val toolCallId: String? = "tool-call-id",
    )

    internal object Strategy {
        internal val simpleGraphStrategy = strategy<String, String>(Parameter.DEFAULT_STRATEGY_NAME) {
            nodeStart then nodeFinish
        }
        internal val simpleFunctionalStrategy =
            functionalStrategy<String, String>(Parameter.DEFAULT_STRATEGY_NAME) { it }

        internal fun getSimpleStrategy(agentType: AgentType) = when (agentType) {
            AgentType.Graph -> simpleGraphStrategy
            AgentType.Functional -> simpleFunctionalStrategy
        }

        internal val singleLLMCallGraphStrategy = strategy(Parameter.DEFAULT_STRATEGY_NAME) {
            val nodeSendInput by nodeLLMRequest("test-llm-call")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
        }
        internal val singleLLMCallFunctionalStrategy =
            functionalStrategy<String, String>(Parameter.DEFAULT_STRATEGY_NAME) { input ->
                requestLLM(input).content
            }

        fun getSingleLLMCallStrategy(agentType: AgentType) = when (agentType) {
            AgentType.Graph -> singleLLMCallGraphStrategy
            AgentType.Functional -> singleLLMCallFunctionalStrategy
        }

        internal val singleToolCallGraphStrategy = strategy(Parameter.DEFAULT_STRATEGY_NAME) {
            val nodeCallLLM by nodeLLMRequest("test-llm-call")
            val nodeExecuteTool by nodeExecuteTool("test-tool-call")
            val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

            edge(nodeStart forwardTo nodeCallLLM)
            edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        }
        internal val singleToolCallFunctionalStrategy =
            functionalStrategy<String, String>(Parameter.DEFAULT_STRATEGY_NAME) { input ->
                var result = requestLLM(input)

                while (result is Message.Tool.Call) {
                    result = sendToolResult(executeTool(result))
                }

                result.content
            }

        fun getSingleToolCallStrategy(agentType: AgentType) = when (agentType) {
            AgentType.Graph -> singleToolCallGraphStrategy
            AgentType.Functional -> singleToolCallFunctionalStrategy
        }
    }

    internal val defaultMockExecutor = getMockExecutor(serializer, testClock) {
        mockLLMAnswer(MOCK_LLM_RESPONSE_PARIS) onRequestEquals USER_PROMPT_PARIS
    }

    //region Agents With Strategies

    internal suspend fun runAgentWithSingleLLMCallStrategy(
        userPrompt: String,
        mockLLMResponse: String,
        verbose: Boolean = true,
        agentType: AgentType,
    ): OpenTelemetryTestData {
        val strategy = getSingleLLMCallStrategy(agentType)

        val executor = getMockExecutor(serializer, testClock) {
            mockLLMAnswer(mockLLMResponse) onRequestEquals userPrompt
        }

        return runAgentWithStrategy(
            strategy = strategy,
            userPrompt = userPrompt,
            executor = executor,
            verbose = verbose,
        )
    }

    internal suspend fun <TArgs, TResult> runAgentWithSingleToolCallStrategy(
        userPrompt: String,
        mockToolCallResponse: MockToolCallResponse<TArgs, TResult>,
        mockLLMResponse: String,
        verbose: Boolean = true,
        agentType: AgentType = AgentType.Graph,
    ): OpenTelemetryTestData {
        val strategy = getSingleToolCallStrategy(agentType)

        val toolRegistry = ToolRegistry {
            tool(mockToolCallResponse.tool)
        }

        val executor = getMockExecutor(serializer, testClock) {
            // Mock tool call
            mockLLMToolCall(
                tool = mockToolCallResponse.tool,
                args = mockToolCallResponse.arguments,
                toolCallId = mockToolCallResponse.toolCallId,
            ) onRequestEquals userPrompt

            // Mock response from the "send tool result" node
            mockLLMAnswer(mockLLMResponse) onRequestContains
                mockToolCallResponse.tool.encodeResultToString(mockToolCallResponse.toolResult, serializer)
        }

        return runAgentWithStrategy(
            strategy = strategy,
            executor = executor,
            toolRegistry = toolRegistry,
            verbose = verbose,
        )
    }

    internal suspend fun runAgentWithStrategy(
        strategy: AIAgentStrategy<String, String, *>,
        agentId: String? = null,
        promptId: String? = null,
        model: LLModel? = null,
        userPrompt: String? = null,
        executor: PromptExecutor? = null,
        toolRegistry: ToolRegistry? = null,
        maxTokens: Int? = null,
        verbose: Boolean = true,
        collectedTestData: OpenTelemetryTestData = OpenTelemetryTestData()
    ): OpenTelemetryTestData {
        val agentId = agentId ?: DEFAULT_AGENT_ID
        val promptId = promptId ?: DEFAULT_PROMPT_ID
        val model = model ?: defaultModel

        return MockSpanExporter().use { mockExporter ->
            collectedTestData.collectedSpans = mockExporter.collectedSpans

            val agentResult = createAgent(
                agentId = agentId,
                strategy = strategy,
                executor = executor,
                promptId = promptId,
                toolRegistry = toolRegistry,
                userPrompt = userPrompt,
                systemPrompt = SYSTEM_PROMPT,
                model = model,
                temperature = TEMPERATURE,
                maxTokens = maxTokens,
            ) {
                addSpanExporter(mockExporter)
                setVerbose(verbose)
            }.use { agent ->
                agent.run(userPrompt ?: USER_PROMPT_PARIS)
            }

            waitSpansCollected(mockExporter)

            collectedTestData.result = agentResult
            collectedTestData
        }
    }

    /**
     * Waits for spans to be collected within a specified timeout period.
     *
     * Note! Use default dispatcher because the [kotlinx.coroutines.test.runTest] wrapper override thread scheduler
     *       and [withTimeoutOrNull] does not wait for a specified timeout.
     */
    private suspend fun waitSpansCollected(mockExporter: MockSpanExporter) = withContext(Dispatchers.Default) {
        val isSpanDataCollected = withTimeoutOrNull(spansCollectionTimeout) {
            // Wait until all spans are collected
            mockExporter.isCollected.first { it }
        } != null

        assertTrue(isSpanDataCollected, "Spans were not collected within the timeout: $spansCollectionTimeout")
    }

    //endregion Agents With Strategies

    //region Agents

    internal suspend fun createAgent(
        agentId: String = DEFAULT_AGENT_ID,
        strategy: AIAgentStrategy<String, String, *>,
        executor: PromptExecutor? = null,
        promptId: String? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = 0.0,
        maxTokens: Int? = null,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        assistantPrompt: String? = null,
        configureOtel: OpenTelemetryConfig.() -> Unit = { }
    ): AIAgent<String, String> {
        val agentService = createAgentService(
            strategy,
            executor,
            promptId,
            toolRegistry,
            model,
            temperature,
            maxTokens,
            systemPrompt,
            userPrompt,
            assistantPrompt,
            configureOtel
        )

        return agentService.createAgent(id = agentId)
    }

    internal fun createAgentService(
        strategy: AIAgentStrategy<String, String, *>,
        executor: PromptExecutor? = null,
        promptId: String? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = 0.0,
        maxTokens: Int? = null,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        assistantPrompt: String? = null,
        configureOtel: OpenTelemetryConfig.() -> Unit = { }
    ): AIAgentService<String, String, *> {
        val agentConfig = AIAgentConfig(
            prompt = prompt(
                id = promptId ?: "Test prompt",
                clock = testClock,
                params = LLMParams(
                    temperature = temperature,
                    maxTokens = maxTokens
                )
            ) {
                systemPrompt?.let { system(systemPrompt) }
                userPrompt?.let { user(userPrompt) }
                assistantPrompt?.let { assistant(assistantPrompt) }
            },
            model = model ?: OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 10,
        )
        val promptExecutor = executor ?: getMockExecutor(serializer, testClock) { }
        val toolRegistry = toolRegistry ?: ToolRegistry.EMPTY

        return when (strategy) {
            is AIAgentGraphStrategy -> AIAgentService(
                promptExecutor = promptExecutor,
                strategy = strategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry
            ) {
                install(OpenTelemetry) { configureOtel() }
            }

            is AIAgentFunctionalStrategy -> AIAgentService(
                promptExecutor = executor ?: getMockExecutor(serializer, testClock) { },
                strategy = strategy,
                agentConfig = agentConfig,
                toolRegistry = toolRegistry
            ) {
                install(OpenTelemetry) { configureOtel() }
            }

            else -> throw IllegalArgumentException("Unsupported strategy type: ${strategy::class.simpleName}")
        }
    }

    //endregion Agents

    //region Messages

    fun toolCallMessage(id: String, name: String, content: String) =
        Message.Tool.Call(id, name, content, ResponseMetaInfo(timestamp = testClock.now()))

    fun assistantMessage(content: String, finishReason: String? = null) =
        Message.Assistant(content, ResponseMetaInfo(timestamp = testClock.now()), finishReason = finishReason)

    //endregion Messages

    //region Attributes

    fun getSystemInstructionsString(messages: List<String>): String {
        val jsonObjects = messages.map { message ->
            JsonObject(
                mapOf(
                    "type" to JsonPrimitive("text"),
                    "content" to JsonPrimitive(message)
                )
            )
        }

        return JsonArray(jsonObjects).toString()
    }

    fun getMessagesString(messages: List<Message>): String {
        return SpanAttributes.Input.Messages(messages).value.value
    }

    fun getToolDefinitionsString(toolDescriptors: List<ai.koog.agents.core.tools.ToolDescriptor>): String {
        return SpanAttributes.Tool.Definitions(toolDescriptors).value.value
    }

    //endregion Attributes
}
