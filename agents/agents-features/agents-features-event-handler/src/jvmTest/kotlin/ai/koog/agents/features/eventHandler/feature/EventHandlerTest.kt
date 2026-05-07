package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionEventContext
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.eventString
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.MockExecutorDSLBuilder
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.ObservablePromptExecutor
import ai.koog.prompt.executor.model.PromptExecutionContext
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorEvent
import ai.koog.prompt.executor.model.StreamingCompleted
import ai.koog.prompt.executor.model.StreamingDispatched
import ai.koog.prompt.executor.model.StreamingFailed
import ai.koog.prompt.executor.model.StreamingRequested
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.LLMChoice
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.utils.io.use
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Instant

class EventHandlerTest {

    @Test
    fun `test event handler for agent without nodes and tools`() = runTest {
        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"
        val agentInput = "Hello, world!!!"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            agent.run(agentInput, null)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: test-agent-id, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: test-agent-id)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }


    @ParameterizedTest
    @MethodSource("llmCallVariants")
    fun `test event handler single node without tools`(variant: ContextualVariant) = runTest {
        val eventsCollector = TestEventsCollector()
        val agentId = "test-agent-id"

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"
        val testLLMResponse = "Test LLM call prompt"

        val strategyName = "tracing-test-strategy"
        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = ToolRegistry { },
            executor = variant.executor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput, null)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: $testLLMResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, tools: [])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, model: ${model.eventString}, tools: [], responses: [role: ${Message.Role.Assistant}, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: $testLLMResponse, output: " +
                "Assistant(parts=[Text(text=Default test response)], metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null), finishReason=null, cacheControl=null))",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @ParameterizedTest
    @MethodSource("llmCallMockExecutorVariants")
    fun `test event handler single node with tools`(variant: MockBasedVariant) = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Call the dummy tool with argument: test"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val strategyName = "test-strategy"

        val mockResponse = "Return test result"

        val agentId = "test-agent-id"
        val model = OpenAIModels.Chat.GPT4o

        val strategy = strategy(strategyName) {
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

        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        createAgent(
            agentId = agentId,
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            toolRegistry = toolRegistry,
            executor = variant.executor {
                mockLLMToolCall(dummyTool, DummyTool.Args("test")) onRequestEquals userPrompt
                mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
            },
            model = model,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            agent.run(userPrompt, null)
        }

        val runId = eventsCollector.runId
        val dummyToolName = dummyTool.name
        val dummyToolDescription = dummyTool.descriptor.description
        val dummyToolArgsEncoded = dummyTool.encodeArgs(DummyTool.Args("test"), serializer)
        val dummyToolResultEncoded = dummyTool.encodeResult(dummyTool.result, serializer)

        val dummyToolReceivedToolResult = ReceivedToolResult(
            id = null,
            tool = dummyToolName,
            toolArgs = dummyToolArgsEncoded,
            toolDescription = dummyToolDescription,
            content = dummyTool.result,
            resultKind = ToolResultKind.Success,
            result = dummyToolResultEncoded
        )

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $userPrompt)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $userPrompt, output: $userPrompt)",
            "OnNodeExecutionStarting (run id: $runId, node: test-llm-call, input: $userPrompt)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt" +
                "}], temperature: $temperature, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt" +
                "}], temperature: $temperature, model: ${model.eventString}, tools: [$dummyToolName], responses: [role: ${Message.Role.Tool}, message: {\"dummy\":\"test\"}])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-llm-call, input: $userPrompt, output: " +
                "Call(id=null, tool=$dummyToolName, parts=[Text(text=$dummyToolArgsEncoded)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)))",
            "OnNodeExecutionStarting (run id: $runId, node: test-tool-call, input: " +
                "Call(id=null, tool=$dummyToolName, parts=[Text(text=$dummyToolArgsEncoded)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)))",
            "OnToolCallStarting (run id: $runId, tool: $dummyToolName, args: $dummyToolArgsEncoded)",
            "OnToolCallCompleted (run id: $runId, tool: $dummyToolName, args: $dummyToolArgsEncoded, result: $dummyToolResultEncoded)",
            "OnNodeExecutionCompleted (run id: $runId, node: test-tool-call, input: " +
                "Call(id=null, tool=$dummyToolName, parts=[Text(text=$dummyToolArgsEncoded)], " +
                "metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)), output: $dummyToolReceivedToolResult)",
            "OnNodeExecutionStarting (run id: $runId, node: test-node-llm-send-tool-result, input: $dummyToolReceivedToolResult)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Tool}, message: $dummyToolArgsEncoded, " +
                "role: ${Message.Role.Tool}, message: ${dummyTool.result}" +
                "}], temperature: $temperature, tools: [$dummyToolName])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Tool}, message: $dummyToolArgsEncoded, " +
                "role: ${Message.Role.Tool}, message: ${dummyTool.result}" +
                "}], temperature: $temperature, model: openai:gpt-4o, tools: [$dummyToolName], responses: [role: ${Message.Role.Assistant}, message: Return test result])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-node-llm-send-tool-result, " +
                "input: $dummyToolReceivedToolResult, " +
                "output: Assistant(parts=[Text(text=$mockResponse)], metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null), finishReason=null, cacheControl=null))",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $mockResponse)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $mockResponse, output: $mockResponse)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $mockResponse)",
            "OnAgentCompleted (agent id: $agentId, run id: $runId, result: $mockResponse)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @ParameterizedTest
    @MethodSource("llmCallVariants")
    fun `test event handler several nodes`(variant: ContextualVariant) = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val agentResult = "Done"

        val strategyName = "tracing-test-strategy"
        val testLLMResponse = "Test LLM call prompt"
        val llmCallWithToolsResponse = "Test LLM call with tools prompt"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { testLLMResponse })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { llmCallWithToolsResponse })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { agentResult })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val agent = createAgent(
            strategy = strategy,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            toolRegistry = toolRegistry,
            executor = variant.executor,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput, null)
        agent.close()

        val runId = eventsCollector.runId
        val toolNames = toolRegistry.tools.joinToString { it.name }
        val firstPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
            "role: ${Message.Role.User}, message: $testLLMResponse" +
            "}], temperature: $temperature"
        val secondPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
            "role: ${Message.Role.User}, message: Test LLM call prompt, " +
            "role: ${Message.Role.Assistant}, message: Default test response, " +
            "role: ${Message.Role.User}, message: $llmCallWithToolsResponse" +
            "}], temperature: $temperature"
        val expectedEvents =
            variant.expectedEventsAsStrings(
                runId = runId,
                promptStr = firstPromptString,
                modelStr = model.eventString,
                toolNames = toolNames,
                response = "role: ${Message.Role.Assistant}, message: Default test response"
            ) +
                variant.expectedEventsAsStrings(
                    runId = runId,
                    promptStr = secondPromptString,
                    modelStr = model.eventString,
                    toolNames = toolNames,
                    response = "role: ${Message.Role.Assistant}, message: Default test response"
                )
        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMCall") }

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test event handler for agent with node execution error`() = runTest {
        val eventsCollector = TestEventsCollector()

        val agentId = "test-agent-id"
        val strategyName = "test-strategy"
        val agentInput = "Hello, world!!!"
        val agentResult = "Done"

        val errorNodeName = "Node with error"
        val testErrorMessage = "Test error"

        val strategy = strategy<String, String>(strategyName) {
            val nodeWithError by node<String, String>(errorNodeName) {
                throw IllegalStateException(testErrorMessage)
            }

            edge(nodeStart forwardTo nodeWithError)
            edge(nodeWithError forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(
            agentId = agentId,
            strategy = strategy,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            val throwable = assertThrows<IllegalStateException> { agent.run(agentInput, null) }
            assertEquals(testErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: $errorNodeName, input: $agentInput)",
            "OnNodeExecutionFailed (run id: $runId, node: $errorNodeName, input: $agentInput, error: $testErrorMessage)",
            "OnAgentExecutionFailed (agent id: $agentId, run id: $runId, error: $testErrorMessage)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler with multiple handlers`() = runTest {
        val collectedEvents = mutableListOf<String>()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        var runId = ""

        val agent = createAgent(
            agentId = "test-agent-id",
            strategy = strategy,
            installFeatures = {
                install(EventHandler) {
                    onAgentStarting { eventContext ->
                        runId = eventContext.runId
                        collectedEvents.add(
                            "OnAgentStarting first (agent id: ${eventContext.agent.id})"
                        )
                    }

                    onAgentStarting { eventContext ->
                        collectedEvents.add(
                            "OnAgentStarting second (agent id: ${eventContext.agent.id})"
                        )
                    }

                    onAgentCompleted { eventContext ->
                        collectedEvents.add(
                            "OnAgentCompleted (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, result: $agentResult)"
                        )
                    }
                }
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput, null)

        val expectedEvents = listOf(
            "OnAgentStarting first (agent id: ${agent.id})",
            "OnAgentStarting second (agent id: ${agent.id})",
            "OnAgentCompleted (agent id: ${agent.id}, run id: $runId, result: $agentResult)",
        )

        assertEquals(expectedEvents.size, collectedEvents.size)
        assertContentEquals(expectedEvents, collectedEvents)
    }

    @Disabled
    @Test
    fun testEventHandlerWithErrors() = runTest {
        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeException("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(DummyTool()) },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        agent.run("Hello, world!!!")
        agent.close()
    }

    @ParameterizedTest
    @MethodSource("streamingEventsSuccessVariants")
    fun `test llm streaming events success`(variant: MockBasedVariant) = runTest {
        val eventsCollector = TestEventsCollector()

        val model = OpenAIModels.Chat.GPT4o
        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0

        val strategyName = "event-handler-streaming-success"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollect)
            edge(streamAndCollect forwardTo nodeFinish transformed { messages -> messages.firstOrNull()?.content ?: "" })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }
        val testLLMResponse = "Default test response"

        createAgent(
            agentId = "test-agent-id",
            strategy = strategy,
            executor = variant.executor {
                mockLLMAnswer(testLLMResponse).asDefaultResponse onUserRequestEquals "Test user message"
            },
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            agent.run("", null)
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt" +
            "}], temperature: $temperature"
        val expectedEvents = variant.expectedEventsAsStrings(
            runId = runId,
            promptStr = expectedPromptString,
            modelStr = model.eventString,
            toolNames = toolRegistry.tools.joinToString { it.name },
            frameContent = testLLMResponse
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @ParameterizedTest
    @MethodSource("streamingEventsFailureVariants")
    fun `test llm streaming events failure`(variant: ContextualVariant) = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val temperature = 1.0
        val model = OpenAIModels.Chat.GPT4o

        val strategyName = "event-handler-streaming-failure"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreamingAndSendResults<String>("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollect)
            edge(streamAndCollect forwardTo nodeFinish transformed { messages ->
                messages.firstOrNull()?.content ?: ""
            })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        createAgent(
            strategy = strategy,
            executor = variant.executor,
            promptId = promptId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            assistantPrompt = assistantPrompt,
            temperature = temperature,
            model = model,
            toolRegistry = toolRegistry,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            val throwable = assertThrows<IllegalStateException> { agent.run("", null) }
            assertEquals(variant.expectedErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId
        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt" +
            "}], temperature: $temperature"
        val expectedEvents = variant.expectedEventsAsStrings(
            runId,
            expectedPromptString,
            model.eventString,
            toolRegistry.tools.joinToString { it.name }
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test subgraph execution events success`() = runTest {
        val eventsCollector = TestEventsCollector()

        val strategyName = "test-strategy"
        val subgraphName = "test-subgraph"
        val subgraphNodeName = "test-subgraph-node"
        val subgraphOutput = "test-subgraph-output"
        val inputRequest = "Test input"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                val subgraphNode by node<String, String>(subgraphNodeName) { subgraphOutput }
                nodeStart then subgraphNode then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler) eventHandlerConfig@{
                    setEventFilter { context ->
                        context is SubgraphExecutionEventContext
                    }
                    eventsCollector.eventHandlerFeatureConfig.invoke(this@eventHandlerConfig)
                }
            }
        ).use { agent ->
            agent.run(inputRequest, null)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnSubgraphExecutionStarting (run id: $runId, subgraph: $subgraphName, input: $inputRequest)",
            "OnSubgraphExecutionCompleted (run id: $runId, subgraph: $subgraphName, input: $inputRequest, output: $subgraphOutput)",
        )

        assertEquals(expectedEvents.size, eventsCollector.collectedEvents.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test subgraph execution events failure`() = runTest {
        val eventsCollector = TestEventsCollector()

        val strategyName = "test-strategy"
        val subgraphName = "test-subgraph"
        val subgraphErrorNodeName = "test-subgraph-error-node"
        val subgraphNodeErrorMessage = "Test subgraph error"
        val inputRequest = "Test input"

        val strategy = strategy<String, String>(strategyName) {
            val subgraph by subgraph<String, String>(subgraphName) {
                val nodeWithError by node<String, String>(subgraphErrorNodeName) {
                    throw IllegalStateException(subgraphNodeErrorMessage)
                }
                nodeStart then nodeWithError then nodeFinish
            }
            nodeStart then subgraph then nodeFinish
        }

        val agentThrowable = createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler) eventHandlerConfig@{
                    setEventFilter { context ->
                        context is SubgraphExecutionEventContext
                    }
                    eventsCollector.eventHandlerFeatureConfig.invoke(this@eventHandlerConfig)
                }
            }
        ).use { agent ->
            assertFails { agent.run(inputRequest, null) }
        }

        assertEquals(subgraphNodeErrorMessage, agentThrowable.message)

        // Check captured events
        val runId = eventsCollector.runId
        val expectedEvents = listOf(
            "OnSubgraphExecutionStarting (run id: $runId, subgraph: $subgraphName, input: $inputRequest)",
            "OnSubgraphExecutionFailed (run id: $runId, subgraph: $subgraphName, input: $inputRequest, error: $subgraphNodeErrorMessage)",
        )

        assertEquals(expectedEvents.size, eventsCollector.collectedEvents.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    companion object {
        private val serializer = KotlinxSerializer()

        @JvmStatic
        fun llmCallVariants(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    Named.of(
                        "Legacy",
                        ContextualVariant(
                            executor = TestLLMExecutor(testClock).asLegacy(),
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMCallStarting,
                                ExpectedLLMEvent.OnLLMCallCompleted,
                            )
                        )
                    )
                ),
                Arguments.of(
                    Named.of(
                        "Observable",
                        ContextualVariant(
                            executor = TestLLMExecutor(testClock),
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMCallStarting,
                                ExpectedLLMEvent.OnLLMCallDispatched,
                                ExpectedLLMEvent.OnLLMCallCompleted,
                            )
                        )
                    )
                )
            )

        @JvmStatic
        fun llmCallMockExecutorVariants(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    Named.of(
                        "Legacy",
                        MockBasedVariant(
                            asLegacy = true,
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMCallStarting,
                                ExpectedLLMEvent.OnLLMCallCompleted,
                            )
                        )
                    )
                ),
                Arguments.of(
                    Named.of(
                        "Observable",
                        MockBasedVariant(
                            asLegacy = false,
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMCallStarting,
                                ExpectedLLMEvent.OnLLMCallDispatched,
                                ExpectedLLMEvent.OnLLMCallCompleted,
                            )
                        )
                    )
                )
            )
        }

        @JvmStatic
        fun streamingEventsSuccessVariants(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    Named.of(
                        "Legacy",
                        MockBasedVariant(
                            asLegacy = true,
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMStreamingStarting,
                                ExpectedLLMEvent.OnTextDeltaFrameReceived,
                                ExpectedLLMEvent.OnTextCompleteFrameReceived,
                                ExpectedLLMEvent.OnEndFrameReceived,
                                ExpectedLLMEvent.OnLLMStreamingCompleted,
                            )
                        )
                    )
                ),
                Arguments.of(
                    Named.of(
                        "Observable",
                        MockBasedVariant(
                            asLegacy = false,
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMStreamingStarting,
                                ExpectedLLMEvent.OnLLMStreamingDispatched,
                                ExpectedLLMEvent.OnTextDeltaFrameReceived,
                                ExpectedLLMEvent.OnTextCompleteFrameReceived,
                                ExpectedLLMEvent.OnEndFrameReceived,
                                ExpectedLLMEvent.OnLLMStreamingCompleted,
                            )
                        )
                    )
                ),
            )

        @JvmStatic
        fun streamingEventsFailureVariants(): Stream<Arguments> {
            val errorMessage = "Test streaming error"
            return Stream.of(
                Arguments.of(
                    Named.of(
                        "Legacy",
                        ContextualVariant(
                            executor = failingStreamingExecutor(errorMessage).asLegacy(),
                            expectedErrorMessage = errorMessage,
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMStreamingStarting,
                                ExpectedLLMEvent.OnLLMStreamingFailed,
                                ExpectedLLMEvent.OnLLMStreamingCompleted
                            )
                        )
                    )
                ),
                Arguments.of(
                    Named.of(
                        "Observable",
                        ContextualVariant(
                            executor = failingStreamingExecutor(errorMessage),
                            expectedErrorMessage = errorMessage,
                            expectedLLMEvents = listOf(
                                ExpectedLLMEvent.OnLLMStreamingStarting,
                                ExpectedLLMEvent.OnLLMStreamingDispatched,
                                ExpectedLLMEvent.OnLLMStreamingFailed,
                                ExpectedLLMEvent.OnLLMStreamingCompleted
                            )
                        )
                    )
                ),
            )
        }

        private fun failingStreamingExecutor(errorMessage: String): ObservablePromptExecutor =
            object : ObservablePromptExecutor() {
                private val sink = MutableSharedFlow<PromptExecutorEvent>()
                override val events: Flow<PromptExecutorEvent> = sink.asSharedFlow()
                override suspend fun execute(
                    prompt: Prompt,
                    model: LLModel,
                    tools: List<ToolDescriptor>,
                    context: PromptExecutionContext
                ): List<Message.Response> = throw UnsupportedOperationException("This executor only supports streaming")

                override fun executeStreaming(
                    prompt: Prompt,
                    model: LLModel,
                    tools: List<ToolDescriptor>,
                    context: PromptExecutionContext
                ): Flow<StreamFrame> = flow {
                    sink.emit(StreamingRequested(context, prompt, model, tools))
                    sink.emit(StreamingDispatched(context, prompt, model, tools))
                    try {
                        throw IllegalStateException(errorMessage)
                    } catch (e: Throwable) {
                        sink.emit(StreamingFailed(context, prompt, model, tools, e))
                        throw e
                    } finally {
                        sink.emit(StreamingCompleted(context, prompt, model, tools))
                    }
                }

                override suspend fun executeMultipleChoices(
                    prompt: Prompt,
                    model: LLModel,
                    tools: List<ToolDescriptor>,
                    context: PromptExecutionContext
                ): List<LLMChoice> = throw UnsupportedOperationException("This executor only supports streaming")

                override suspend fun moderate(
                    prompt: Prompt,
                    model: LLModel,
                    context: PromptExecutionContext
                ): ModerationResult = throw UnsupportedOperationException("This executor only supports streaming")

                override fun close() {}
            }

    }

    //region Private Methods

    private fun nodeException(name: String? = null): AIAgentNodeDelegate<String, Message.Response> =
        node(name) { throw IllegalStateException("Test exception") }

}

class MockBasedVariant(
    val expectedLLMEvents: List<ExpectedLLMEvent>,
    val asLegacy: Boolean = false,
    val expectedErrorMessage: String? = null,
) {
    fun executor(init: MockExecutorDSLBuilder.() -> Unit): PromptExecutor {
        val mockExecutor = getMockExecutor(clock = testClock, init = init)
        return if (asLegacy) mockExecutor.asLegacy() else mockExecutor
    }

    fun expectedEventsAsStrings(
        runId: String,
        promptStr: String,
        modelStr: String,
        toolNames: String,
        response: String? = null,
        frameContent: String? = null,
    ): List<String> = expectedLLMEvents.map {
        it.toStringEvent(runId, promptStr, modelStr, toolNames, response, expectedErrorMessage, frameContent)
    }
}

data class ContextualVariant(
    val executor: PromptExecutor,
    val expectedErrorMessage: String? = null,
    val expectedLLMEvents: List<ExpectedLLMEvent>,
    val customToolRegistry: ToolRegistry? = null,
) {

    fun expectedEventsAsStrings(
        runId: String,
        promptStr: String,
        modelStr: String,
        toolNames: String,
        response: String? = null,
        frameContent: String? = null
    ): List<String> = expectedLLMEvents.map {
        it.toStringEvent(runId, promptStr, modelStr, toolNames, response, expectedErrorMessage, frameContent)
    }
}

enum class ExpectedLLMEvent {
    OnLLMCallStarting,
    OnLLMCallDispatched,
    OnLLMCallCompleted,
    OnLLMStreamingStarting,
    OnLLMStreamingDispatched,
    OnLLMStreamingFrameReceived,
    OnTextDeltaFrameReceived,
    OnTextCompleteFrameReceived,
    OnEndFrameReceived,
    OnLLMStreamingFailed,
    OnLLMStreamingCompleted;

    fun toStringEvent(
        runId: String,
        promptStr: String,
        modelStr: String,
        toolNames: String,
        response: String? = null,
        errorMessage: String? = null,
        frameContent: String? = null,
    ): String = when (this) {
        OnLLMCallStarting -> "OnLLMCallStarting (run id: $runId, prompt: $promptStr, tools: [$toolNames])"
        OnLLMCallDispatched -> "OnLLMCallDispatched (run id: $runId, prompt: $promptStr, model: $modelStr, tools: [$toolNames])"
        OnLLMCallCompleted -> "OnLLMCallCompleted (run id: $runId, prompt: $promptStr, model: $modelStr, tools: [$toolNames], responses: [$response])"
        OnLLMStreamingStarting -> "OnLLMStreamingStarting (run id: $runId, prompt: $promptStr, model: $modelStr, tools: [$toolNames])"
        OnLLMStreamingDispatched -> "OnLLMStreamingDispatched (run id: $runId, prompt: $promptStr, model: $modelStr, tools: [$toolNames])"
        OnLLMStreamingFrameReceived -> "OnLLMStreamingFrameReceived (run id: $runId, frame: $frameContent)"
        OnTextDeltaFrameReceived -> "OnLLMStreamingFrameReceived (run id: $runId, frame: TextDelta(text=$frameContent, index=0))"
        OnTextCompleteFrameReceived -> "OnLLMStreamingFrameReceived (run id: $runId, frame: TextComplete(text=$frameContent, index=0))"
        OnEndFrameReceived -> "OnLLMStreamingFrameReceived (run id: $runId, frame: End(finishReason=null, metaInfo=ResponseMetaInfo(timestamp=${Instant.DISTANT_PAST}, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}, metadata=null)))"
        OnLLMStreamingFailed -> "OnLLMStreamingFailed (run id: $runId, error: $errorMessage)"
        OnLLMStreamingCompleted -> "OnLLMStreamingCompleted (run id: $runId, prompt: $promptStr, model: $modelStr, tools: [$toolNames])"
    }
}

//endregion Private Methods


private fun ObservablePromptExecutor.asLegacy(): PromptExecutor = object : PromptExecutor() {
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<Message.Response> = this@asLegacy.execute(prompt, model, tools)

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = this@asLegacy.executeStreaming(prompt, model, tools)

    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult = this@asLegacy.moderate(prompt, model)

    override suspend fun executeMultipleChoices(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): List<LLMChoice> = this@asLegacy.executeMultipleChoices(prompt, model, tools)

    override fun close() = this@asLegacy.close()
}
