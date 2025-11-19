package ai.koog.agents.features.opentelemetry

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.GraphAIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.element.getNodeInfoElement
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_AGENT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.DEFAULT_PROMPT_ID
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.SYSTEM_PROMPT
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.TEMPERATURE
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.USER_PROMPT_PARIS
import ai.koog.agents.features.opentelemetry.OpenTelemetryTestAPI.Parameter.defaultModel
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.features.opentelemetry.mock.MockSpanExporter
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.utils.io.use
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal object OpenTelemetryTestAPI {

    internal val testClock: Clock = object : Clock {
        override fun now(): Instant = Instant.parse("2023-01-01T00:00:00Z")
    }

    internal object Parameter {
        internal const val DEFAULT_AGENT_ID = "test-agent-id"
        internal const val DEFAULT_PROMPT_ID = "test-prompt-id"
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

    //region Agents With Strategies

    internal suspend fun runAgentWithSingleLLMCallStrategy(
        userPrompt: String,
        mockLLMResponse: String,
        verbose: Boolean = true,
    ): OpenTelemetryTestData {
        val strategy = strategy("test-single-llm-strategy") {
            val nodeSendInput by nodeLLMRequest("test-llm-call")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
        }

        val executor = getMockExecutor(clock = testClock) {
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
    ): OpenTelemetryTestData {
        val strategy = strategy("test-tool-calls-strategy") {
            val nodeSendInput by nodeLLMRequest("test-llm-call")
            val nodeExecuteTool by nodeExecuteTool("test-tool-call")
            val nodeSendToolResult by nodeLLMSendToolResult("test-node-llm-send-tool-result")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
        }

        val toolRegistry = ToolRegistry {
            tool(mockToolCallResponse.tool)
        }

        val executor = getMockExecutor(clock = testClock) {
            // Mock tool call
            mockLLMToolCall(
                tool = mockToolCallResponse.tool,
                args = mockToolCallResponse.arguments,
                toolCallId = mockToolCallResponse.toolCallId,
            ) onRequestEquals userPrompt

            // Mock response from the "send tool result" node
            mockLLMAnswer(mockLLMResponse) onRequestContains
                mockToolCallResponse.tool.encodeResultToString(mockToolCallResponse.toolResult)
        }

        return runAgentWithStrategy(
            strategy = strategy,
            executor = executor,
            toolRegistry = toolRegistry,
            verbose = verbose,
        )
    }

    internal suspend fun runAgentWithStrategy(
        strategy: AIAgentGraphStrategy<String, String>,
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
            collectedTestData.runIds = mockExporter.runIds

            val agentResult = createAgent(
                agentId = agentId,
                strategy = strategy,
                executor = executor,
                promptId = promptId,
                toolRegistry = toolRegistry,
                systemPrompt = SYSTEM_PROMPT,
                model = model,
                temperature = TEMPERATURE,
                maxTokens = maxTokens,
            ) {
                install(OpenTelemetry) {
                    addSpanExporter(mockExporter)
                    setVerbose(verbose)
                }

                installNodeIdsCollector().also { collectedTestData.collectedNodeIds = it }
            }.use { agent ->
                agent.run(userPrompt ?: USER_PROMPT_PARIS)
            }

            collectedTestData.result = agentResult
            collectedTestData
        }
    }

    //endregion Agents With Strategies

    //region Agents

    internal suspend fun createAgent(
        agentId: String = "test-agent-id",
        strategy: AIAgentGraphStrategy<String, String>,
        executor: PromptExecutor? = null,
        promptId: String? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = 0.0,
        maxTokens: Int? = null,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        assistantPrompt: String? = null,
        installFeatures: GraphAIAgent.FeatureContext.() -> Unit = { }
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
            installFeatures
        )

        return agentService.createAgent(id = agentId)
    }

    internal fun createAgentService(
        strategy: AIAgentGraphStrategy<String, String>,
        executor: PromptExecutor? = null,
        promptId: String? = null,
        toolRegistry: ToolRegistry? = null,
        model: LLModel? = null,
        temperature: Double? = 0.0,
        maxTokens: Int? = null,
        systemPrompt: String? = null,
        userPrompt: String? = null,
        assistantPrompt: String? = null,
        installFeatures: GraphAIAgent.FeatureContext.() -> Unit = { }
    ): GraphAIAgentService<String, String> {
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

        return AIAgentService(
            promptExecutor = executor ?: getMockExecutor(clock = testClock) { },
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry ?: ToolRegistry { },
            installFeatures = installFeatures,
        )
    }

    //endregion Agents

    //region Features

    internal fun GraphAIAgent.FeatureContext.installNodeIdsCollector(): List<NodeInfo> {
        val nodesInfo = mutableListOf<NodeInfo>()
        install(EventHandler.Feature) {
            onNodeExecutionStarting { eventContext ->
                getNodeInfoElement()?.id?.let { nodeId ->
                    nodesInfo.add(NodeInfo(nodeName = eventContext.node.name, nodeId = nodeId))
                }
            }

            onSubgraphExecutionStarting { eventContext ->
                getNodeInfoElement()?.id?.let { nodeId ->
                    nodesInfo.add(NodeInfo(nodeName = eventContext.subgraph.name, nodeId = nodeId))
                }
            }
        }
        return nodesInfo
    }

    //endregion Features
}
