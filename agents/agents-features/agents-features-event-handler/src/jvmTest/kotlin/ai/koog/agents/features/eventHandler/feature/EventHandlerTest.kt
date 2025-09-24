package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.eventString
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventHandlerTest {

    @Test
    fun `test event handler for agent without nodes and tools`() = runBlocking {
        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

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
    fun `test event handler single node without tools`() = runBlocking {
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
        agent.run(agentInput)
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
                "Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
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
    fun `test event handler single node with tools`() = runBlocking {
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

        val mockExecutor = getMockExecutor(clock = testClock) {
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
            promptExecutor = mockExecutor,
            model = model,
        ) {
            install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
        }.use { agent ->
            agent.run(userPrompt)
        }

        val runId = eventsCollector.runId

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
                "}], temperature: $temperature, model: ${model.eventString}, tools: [${dummyTool.name}], responses: [role: ${Message.Role.Tool}, message: {\"dummy\":\"test\"}])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-llm-call, input: $userPrompt, output: " +
                "Call(id=null, tool=${dummyTool.name}, content={\"dummy\":\"test\"}, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={})))",
            "OnNodeExecutionStarting (run id: $runId, node: test-tool-call, input: " +
                "Call(id=null, tool=${dummyTool.name}, content={\"dummy\":\"test\"}, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={})))",
            "OnToolCallStarting (run id: $runId, tool: ${dummyTool.name}, args: Args(dummy=test))",
            "OnToolCallCompleted (run id: $runId, tool: ${dummyTool.name}, args: Args(dummy=test), result: ${dummyTool.result})",
            "OnNodeExecutionCompleted (run id: $runId, node: test-tool-call, input: " +
                "Call(id=null, tool=${dummyTool.name}, content={\"dummy\":\"test\"}, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={})), output: ReceivedToolResult(id=null, tool=${dummyTool.name}, content=${dummyTool.result}, result=${dummyTool.result}))",
            "OnNodeExecutionStarting (run id: $runId, node: test-node-llm-send-tool-result, input: ReceivedToolResult(id=null, tool=${dummyTool.name}, content=${dummyTool.result}, result=${dummyTool.result}))",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Tool}, message: {\"dummy\":\"test\"}, " +
                "role: ${Message.Role.Tool}, message: ${dummyTool.result}" +
                "}], temperature: $temperature, tools: [${dummyTool.name}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Tool}, message: {\"dummy\":\"test\"}, " +
                "role: ${Message.Role.Tool}, message: ${dummyTool.result}" +
                "}], temperature: $temperature, model: openai:gpt-4o, tools: [${dummyTool.name}], responses: [role: ${Message.Role.Assistant}, message: Return test result])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-node-llm-send-tool-result, input: " +
                "ReceivedToolResult(id=null, tool=${dummyTool.name}, content=${dummyTool.result}, result=${dummyTool.result}), output: Assistant(content=Return test result, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
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
    fun `test event handler several nodes`() = runBlocking {
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
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: test-agent-id, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: $testLLMResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: $testLLMResponse" +
                "}], temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}], responses: [role: ${Message.Role.Assistant}, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: $testLLMResponse, output: " +
                "Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call with tools, input: $llmCallWithToolsResponse)",
            "OnLLMCallStarting (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: Test LLM call prompt, " +
                "role: ${Message.Role.Assistant}, message: Default test response, " +
                "role: ${Message.Role.User}, message: $llmCallWithToolsResponse" +
                "}], temperature: $temperature, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: $promptId, messages: [{" +
                "role: ${Message.Role.System}, message: $systemPrompt, " +
                "role: ${Message.Role.User}, message: $userPrompt, " +
                "role: ${Message.Role.Assistant}, message: $assistantPrompt, " +
                "role: ${Message.Role.User}, message: Test LLM call prompt, " +
                "role: ${Message.Role.Assistant}, message: Default test response, " +
                "role: ${Message.Role.User}, message: $llmCallWithToolsResponse" +
                "}], temperature: $temperature, model: openai:gpt-4o, tools: [${toolRegistry.tools.joinToString { it.name }}], responses: [role: ${Message.Role.Assistant}, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call with tools, input: $llmCallWithToolsResponse, output: " +
                "Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
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
    fun `test event handler for agent with node execution error`() = runBlocking {
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
            val throwable = assertThrows<IllegalStateException> { agent.run(agentInput) }
            assertEquals(testErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnAgentStarting (agent id: $agentId, run id: $runId)",
            "OnStrategyStarting (run id: $runId, strategy: $strategyName)",
            "OnNodeExecutionStarting (run id: $runId, node: __start__, input: $agentInput)",
            "OnNodeExecutionCompleted (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnNodeExecutionStarting (run id: $runId, node: $errorNodeName, input: $agentInput)",
            "OnNodeExecutionFailed (run id: $runId, node: $errorNodeName, error: $testErrorMessage)",
            "OnAgentExecutionFailed (agent id: $agentId, run id: $runId, error: $testErrorMessage)",
            "OnAgentClosing (agent id: $agentId)",
        )

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler with multiple handlers`() = runBlocking {
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
        agent.run(agentInput)

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
    fun testEventHandlerWithErrors() = runBlocking {
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
    fun `test llm streaming events success`() = runBlocking {
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
        val executor = getMockExecutor {
            mockLLMAnswer(testLLMResponse).asDefaultResponse onUserRequestEquals "Test user message"
        }

        createAgent(
            agentId = "test-agent-id",
            strategy = strategy,
            promptExecutor = executor,
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
            agent.run("")
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt" +
            "}]"

        val expectedEvents = listOf(
            "OnLLMStreamingStarting (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
            "OnLLMStreamingFrameReceived (run id: $runId, frame: Append(text=$testLLMResponse))",
            "OnLLMStreamingCompleted (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name }}])",
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    @Test
    fun `test llm streaming events failure`() = runBlocking {
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
            edge(streamAndCollect forwardTo nodeFinish transformed { messages -> messages.firstOrNull()?.content ?: "" })
        }

        val toolRegistry = ToolRegistry { tool(DummyTool()) }

        val testStreamingErrorMessage = "Test streaming error"

        val testStreamingExecutor = object : PromptExecutor {
            override suspend fun execute(
                prompt: Prompt,
                model: ai.koog.prompt.llm.LLModel,
                tools: List<ai.koog.agents.core.tools.ToolDescriptor>
            ): List<Message.Response> = emptyList()

            override fun executeStreaming(
                prompt: Prompt,
                model: ai.koog.prompt.llm.LLModel,
                tools: List<ai.koog.agents.core.tools.ToolDescriptor>
            ): Flow<StreamFrame> = flow {
                throw IllegalStateException(testStreamingErrorMessage)
            }

            override suspend fun moderate(
                prompt: Prompt,
                model: ai.koog.prompt.llm.LLModel
            ): ai.koog.prompt.dsl.ModerationResult {
                throw UnsupportedOperationException("Not used in test")
            }
        }

        createAgent(
            strategy = strategy,
            promptExecutor = testStreamingExecutor,
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
            val throwable = assertThrows<IllegalStateException> { agent.run("") }
            assertEquals(testStreamingErrorMessage, throwable.message)
        }

        val runId = eventsCollector.runId

        val actualEvents = eventsCollector.collectedEvents.filter { it.startsWith("OnLLMStreaming") }

        val expectedPromptString = "id: $promptId, messages: [{" +
            "role: ${Message.Role.System}, message: $systemPrompt, " +
            "role: ${Message.Role.User}, message: $userPrompt, " +
            "role: ${Message.Role.Assistant}, message: $assistantPrompt" +
            "}]"

        val expectedEvents = listOf(
            "OnLLMStreamingStarting (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name}}])",
            "OnLLMStreamingFailed (run id: $runId, error: $testStreamingErrorMessage)",
            "OnLLMStreamingCompleted (run id: $runId, prompt: $expectedPromptString, temperature: $temperature, model: ${model.eventString}, tools: [${toolRegistry.tools.joinToString { it.name}}])",
        )

        assertEquals(expectedEvents.size, actualEvents.size)
        assertContentEquals(expectedEvents, actualEvents)
    }

    fun AIAgentSubgraphBuilderBase<*, *>.nodeException(name: String? = null): AIAgentNodeDelegate<String, Message.Response> =
        node(name) { throw IllegalStateException("Test exception") }
}
