package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.ToolCalls
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreaming
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.ToolResultKind
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionEventContext
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.eventString
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.collectText
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import ai.koog.utils.io.use
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventHandlerTest {

    private val serializer = KotlinxSerializer()

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

    @Test
    fun `test event handler single node without tools`() = runTest {
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
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput, null)
        agent.close()

        val runId = eventsCollector.runId

        val expectedPromptString = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, testLLMResponse))
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val expectedAssistantMessage =
            "Assistant(parts=[Text(text=Default test response, cacheControl=null)], metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, modelId=null, metadata=null), finishReason=null, rawResponse=null, id=null)"

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: $testLLMResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: $expectedPromptString, tools: [])",
            "OnLLMCallCompleted (run id: $runId, prompt: $expectedPromptString, model: ${model.eventString}, tools: [], responses: [${expectedMessage(Message.Role.Assistant, "Default test response").trim('{', '}')}])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: $testLLMResponse, output: $expectedAssistantMessage)",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node with tools`() = runTest {
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
            val nodeExecuteTool by nodeExecuteTools("test-tool-call")
            val nodeSendToolResult by nodeLLMSendToolResults("test-node-llm-send-tool-result")

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeExecuteTool onToolCalls { true })
            edge(nodeSendInput forwardTo nodeFinish onTextMessage { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
        }

        val dummyTool = DummyTool()

        val toolRegistry = ToolRegistry {
            tool(dummyTool)
        }

        val mockExecutor = getMockExecutor(serializer, clock = testClock) {
            mockLLMToolCall(dummyTool, DummyTool.Args("test")) onRequestEquals userPrompt
            mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
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
            executor = mockExecutor,
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
            output = dummyTool.result,
            resultKind = ToolResultKind.Success,
            result = dummyToolResultEncoded,
            resultObject = "Dummy result",
            parts = listOf(MessagePart.Text(dummyTool.result)),
        )

        val toolCallPart = "{type: Call, tool: $dummyToolName, args: $dummyToolArgsEncoded}"
        val toolResultPart = "{type: Result, tool: $dummyToolName, output: ${dummyTool.result}}"

        val expectedPromptFirstCall = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt))
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val expectedPromptSecondCall = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append("{role: ${Message.Role.Assistant}, parts: [$toolCallPart]}").append(", ")
            .append("{role: ${Message.Role.User}, parts: [$toolResultPart]}")
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val toolCallAssistantObj =
            "Assistant(parts=[Call(id=null, tool=$dummyToolName, args=$dummyToolArgsEncoded, cacheControl=null)], metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, modelId=null, metadata=null), finishReason=null, rawResponse=null, id=null)"
        val toolCallsInput = "ToolCalls(toolCalls=[Call(id=null, tool=$dummyToolName, args=$dummyToolArgsEncoded, cacheControl=null)])"
        val receivedToolResults = "ReceivedToolResults(toolResults=[$dummyToolReceivedToolResult])"
        val finalAssistantObj =
            "Assistant(parts=[Text(text=$mockResponse, cacheControl=null)], metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, modelId=null, metadata=null), finishReason=null, rawResponse=null, id=null)"

        val toolCallResponseEntry =
            "role: ${Message.Role.Assistant}, parts: [$toolCallPart]"
        val finalResponseEntry =
            expectedMessage(Message.Role.Assistant, mockResponse).trim('{', '}')

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $userPrompt)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $userPrompt, output: $userPrompt)",
            "OnNodeExecutionStarting (run id: $runId, node: test-llm-call, input: $userPrompt)",
            "OnLLMCallStarting (run id: $runId, prompt: $expectedPromptFirstCall, tools: [$dummyToolName])",
            "OnLLMCallCompleted (run id: $runId, prompt: $expectedPromptFirstCall, model: ${model.eventString}, tools: [$dummyToolName], responses: [$toolCallResponseEntry])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-llm-call, input: $userPrompt, output: $toolCallAssistantObj)",
            "OnNodeExecutionStarting (run id: $runId, node: test-tool-call, input: $toolCallsInput)",
            "OnToolCallStarting (run id: $runId, tool: $dummyToolName, args: $dummyToolArgsEncoded)",
            "OnToolCallCompleted (run id: $runId, tool: $dummyToolName, args: $dummyToolArgsEncoded, result: $dummyToolResultEncoded)",
            "OnNodeExecutionCompleted (run id: $runId, node: test-tool-call, input: $toolCallsInput, output: $receivedToolResults)",
            "OnNodeExecutionStarting (run id: $runId, node: test-node-llm-send-tool-result, input: $receivedToolResults)",
            "OnLLMCallStarting (run id: $runId, prompt: $expectedPromptSecondCall, tools: [$dummyToolName])",
            "OnLLMCallCompleted (run id: $runId, prompt: $expectedPromptSecondCall, model: ${model.eventString}, tools: [$dummyToolName], responses: [$finalResponseEntry])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-node-llm-send-tool-result, input: $receivedToolResults, output: $finalAssistantObj)",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $mockResponse)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $mockResponse, output: $mockResponse)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $mockResponse)",
            "OnAgentCompleted (agent id: $agentId, run id: $runId, result: $mockResponse)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler several nodes`() = runTest {
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
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput, null)
        agent.close()

        val runId = eventsCollector.runId
        val defaultResponse = "Default test response"

        val expectedPromptFirstCall = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, testLLMResponse))
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val expectedPromptSecondCall = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, testLLMResponse)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, defaultResponse)).append(", ")
            .append(expectedMessage(Message.Role.User, llmCallWithToolsResponse))
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val expectedAssistantMessage =
            "Assistant(parts=[Text(text=$defaultResponse, cacheControl=null)], metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, modelId=null, metadata=null), finishReason=null, rawResponse=null, id=null)"

        val expectedTools = toolRegistry.tools.joinToString { it.name }
        val responseEntry = expectedMessage(Message.Role.Assistant, defaultResponse).trim('{', '}')

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: test-agent-id, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: $testLLMResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: $expectedPromptFirstCall, tools: [$expectedTools])",
            "OnLLMCallCompleted (run id: $runId, prompt: $expectedPromptFirstCall, model: ${model.eventString}, tools: [$expectedTools], responses: [$responseEntry])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: $testLLMResponse, output: $expectedAssistantMessage)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call with tools, input: $llmCallWithToolsResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: $expectedPromptSecondCall, tools: [$expectedTools])",
            "OnLLMCallCompleted (run id: $runId, prompt: $expectedPromptSecondCall, model: ${model.eventString}, tools: [$expectedTools], responses: [$responseEntry])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call with tools, input: $llmCallWithToolsResponse, output: $expectedAssistantMessage)",
            "OnNodeExecutionStarting (run id: $runId, node: __finish__, input: $agentResult)",
            "OnNodeExecutionCompleted (run id: $runId, node: __finish__, input: $agentResult, output: $agentResult)",
            "OnStrategyCompleted (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentCompleted (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentClosing (agent id: test-agent-id)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
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
                            "OnAgentCompleted (agent id: ${eventContext.agent.id}, run id: ${eventContext.runId}, result: $agentResult)"
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

    @Test
    fun `test llm streaming events success`() = runTest {
        val eventsCollector = TestEventsCollector()

        val model = OpenAIModels.Chat.GPT4o
        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val agentInput = "Test input"
        val temperature = 1.0

        val strategyName = "event-handler-streaming-success"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreaming("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollect)
            edge(
                streamAndCollect forwardTo nodeFinish transformed { messages ->
                    messages.collectText()
                }
            )
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val testLLMResponse = "Default test response"
        val executor = getMockExecutor(serializer, clock = testClock) {
            mockLLMAnswer(testLLMResponse).asDefaultResponse onUserRequestEquals "Test user message"
        }

        createAgent(
            agentId = "test-agent-id",
            strategy = strategy,
            executor = executor,
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
            agent.run(agentInput, null)
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, agentInput))
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val expectedEvents = listOf(
            "OnLLMStreamingStarting (run id: $runId, prompt: $expectedPromptString, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMStreamingFrameReceived (run id: $runId, frame: TextDelta(text=$testLLMResponse, index=0))",
            "OnLLMStreamingFrameReceived (run id: $runId, frame: TextComplete(text=$testLLMResponse, index=0))",
            "OnLLMStreamingFrameReceived (run id: $runId, frame: End(finishReason=null, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, modelId=null, metadata=null)))",
            "OnLLMStreamingCompleted (run id: $runId, prompt: $expectedPromptString, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test llm streaming events failure`() = runTest {
        val eventsCollector = TestEventsCollector()

        val promptId = "Test prompt Id"
        val systemPrompt = "Test system message"
        val userPrompt = "Test user message"
        val assistantPrompt = "Test assistant response"
        val agentInput = "Test input"
        val temperature = 1.0

        val model = OpenAIModels.Chat.GPT4o

        val strategyName = "event-handler-streaming-failure"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreaming("stream-and-collect")

            edge(nodeStart forwardTo streamAndCollect)
            edge(
                streamAndCollect forwardTo nodeFinish transformed { messages ->
                    messages.collectText()
                }
            )
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val testStreamingErrorMessage = "Test streaming error"

        val testStreamingExecutor = object : PromptExecutor() {
            override suspend fun execute(
                prompt: Prompt,
                model: ai.koog.prompt.llm.LLModel,
                tools: List<ToolDescriptor>
            ): Message.Assistant {
                throw IllegalStateException(testStreamingErrorMessage)
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: ai.koog.prompt.llm.LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                throw IllegalStateException(testStreamingErrorMessage)
            }

            override suspend fun moderate(
                prompt: Prompt,
                model: ai.koog.prompt.llm.LLModel
            ): ai.koog.prompt.dsl.ModerationResult {
                throw UnsupportedOperationException("Not used in test")
            }

            override fun close() {}
        }

        createAgent(
            strategy = strategy,
            executor = testStreamingExecutor,
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
            val throwable = assertThrows<IllegalStateException> { agent.run(agentInput, null) }
            assertEquals(testStreamingErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = StringBuilder()
            .append("id: ").append(promptId)
            .append(", messages: [")
            .append(expectedMessage(Message.Role.System, systemPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, userPrompt)).append(", ")
            .append(expectedMessage(Message.Role.Assistant, assistantPrompt)).append(", ")
            .append(expectedMessage(Message.Role.User, agentInput))
            .append("]")
            .append(", temperature: ").append(temperature)
            .toString()

        val expectedEvents = listOf(
            "OnLLMStreamingStarting (run id: $runId, prompt: $expectedPromptString, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMStreamingFailed (run id: $runId, error: $testStreamingErrorMessage)",
            "OnLLMStreamingCompleted (run id: $runId, prompt: $expectedPromptString, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test event handler reports effective model in OnLLMCallCompleted`() = runTest {
        val eventsCollector = TestEventsCollector()

        val requestedModel = OpenAIModels.Chat.GPT4o
        val effectiveModel = AnthropicModels.Sonnet_4_5

        val strategy = strategy<String, String>("model-resolution-strategy") {
            val llmCallNode by nodeLLMRequest("test LLM call")
            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { "Done" })
        }

        createAgent(
            strategy = strategy,
            model = requestedModel,
            executor = ModelResolvingTestExecutor(testClock, effectiveModel),
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            agent.run("Hello, world!!!", null)
        }

        val llmCallCompletedEvents = eventsCollector.collectedEvents.filter {
            it.startsWith("OnLLMCallCompleted")
        }
        assertEquals(1, llmCallCompletedEvents.size)
        llmCallCompletedEvents.forEach { event ->
            assertTrue(
                event.contains("model: ${effectiveModel.eventString}"),
                "Expected effective model in event but got: $event"
            )
            assertFalse(
                event.contains(requestedModel.eventString),
                "Did not expect requested model in event but got: $event"
            )
        }
    }

    @Test
    fun `test event handler reports effective model in OnLLMStreaming events`() = runTest {
        val eventsCollector = TestEventsCollector()

        val requestedModel = OpenAIModels.Chat.GPT4o
        val effectiveModel = AnthropicModels.Sonnet_4_5

        val strategyName = "model-resolution-streaming-strategy"
        val strategy = strategy<String, String>(strategyName) {
            val streamAndCollect by nodeLLMRequestStreaming("stream-and-collect")
            edge(nodeStart forwardTo streamAndCollect)
            edge(
                streamAndCollect forwardTo nodeFinish transformed { messages ->
                    messages.collectText()
                }
            )
        }

        createAgent(
            strategy = strategy,
            model = requestedModel,
            executor = ModelResolvingTestExecutor(testClock, effectiveModel),
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        ).use { agent ->
            agent.run("Hello, world!!!", null)
        }

        val streamingModelEvents = eventsCollector.collectedEvents.filter {
            it.startsWith("OnLLMStreamingStarting") || it.startsWith("OnLLMStreamingCompleted")
        }
        assertEquals(2, streamingModelEvents.size)
        streamingModelEvents.forEach { event ->
            assertTrue(
                event.contains("model: ${effectiveModel.eventString}"),
                "Expected effective model in event but got: $event"
            )
            assertFalse(
                event.contains(requestedModel.eventString),
                "Did not expect requested model in event but got: $event"
            )
        }
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

    @Serializable
    data class CustomInput(
        val question: String
    )

    @Serializable
    data class CustomOutput(
        val x: Int,
        val y: String
    )

    object GuesserTool : Tool<CustomInput, CustomOutput>(
        argsType = typeToken<CustomInput>(),
        resultType = typeToken<CustomOutput>(),
        name = "guesser",
        description = "Very important tool. You MUST call it ALWAYS and exactly once!"
    ) {
        override suspend fun execute(args: CustomInput): CustomOutput = CustomOutput(x = 100500, y = "Hidden Value")

        override fun encodeResultToString(result: CustomOutput, serializer: JSONSerializer): String {
            return "encoded_result(\"${result.y}\")"
        }
    }

    @OptIn(InternalAgentsApi::class)
    @Test
    fun `test ReceivedToolResult contains resultObject`() = runTest {
        val promptExecutor = getMockExecutor {
            mockLLMToolCall(
                GuesserTool,
                CustomInput(question = "What is the secret value?")
            ) onRequestEquals "Tell me the secret!"

            mockLLMAnswer("Done! Value is Hidden Value") onRequestEquals "encoded_result(\"Hidden Value\")"
        }

        val events = mutableListOf<String>()

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            systemPrompt = """
                    You are a helpful assistant.
                    You must use `guesser` tool to answer all questions.
            """.trimIndent(),
            toolRegistry = ToolRegistry {
                tool(GuesserTool)
            },
            strategy = singleRunStrategy(parallelTools = false),
            llmModel = AnthropicModels.Sonnet_4_5
        ) {
            handleEvents {
                onToolCallStarting { ctx ->
                    events += "onToolCallStarting(${ctx.toolName}, args=${ctx.toolArgs})"
                }
                onNodeExecutionCompleted { ctx ->
                    if (ctx.node.name == "nodeExecuteTool") {
                        val toolResult = (ctx.output as ReceivedToolResults).toolResults.single()
                        events += "finished: nodeExecuteTool(tool=${toolResult.tool}, output=${toolResult.output})"
                    }
                }
                onNodeExecutionStarting { ctx ->
                    val input = ctx.input
                    if (input is ToolCalls) {
                        val toolCall = input.toolCalls.single()
                        events += "started: nodeExecuteTool(tool=${toolCall.tool}, content=${toolCall.args})"
                    }
                }
                onToolCallCompleted { ctx ->
                    events += "onToolCallCompleted(guesser, toolResult=${ctx.toolResult})"
                }
                onLLMCallStarting { ctx ->
                    val lastText = (ctx.prompt.messages.last() as? Message.User)?.parts
                        ?.joinToString(separator = "\n") { part ->
                            when (part) {
                                is MessagePart.Text -> part.text
                                is MessagePart.Tool.Result -> part.output
                                else -> ""
                            }
                        } ?: ""
                    events += "onLLMCallStarting($lastText)"
                }
            }
        }

        val result = agent.run("Tell me the secret!")
        assertEquals("Done! Value is Hidden Value", result)

        val expectedEvents = listOf(
            "onLLMCallStarting(Tell me the secret!)",
            "started: nodeExecuteTool(tool=guesser, content={\"question\":\"What is the secret value?\"})",
            "onToolCallStarting(guesser, args={\"question\":\"What is the secret value?\"})",
            "onToolCallCompleted(guesser, toolResult={\"x\":100500, \"y\":\"Hidden Value\"})",
            "finished: nodeExecuteTool(tool=guesser, output=encoded_result(\"Hidden Value\"))",
            "onLLMCallStarting(encoded_result(\"Hidden Value\"))"
        )

        assertEquals(expectedEvents.size, events.size)
        assertContentEquals(expectedEvents, events)
    }

    //region Private Methods

    private fun nodeException(name: String? = null): AIAgentNodeDelegate<String, Message.Assistant> =
        node(name) { throw IllegalStateException("Test exception") }

    private fun expectedMessage(role: Message.Role, text: String): String =
        "{role: $role, parts: [{type: ${MessagePart.Text::class.simpleName}, text: $text}]}"

    //endregion Private Methods
}
