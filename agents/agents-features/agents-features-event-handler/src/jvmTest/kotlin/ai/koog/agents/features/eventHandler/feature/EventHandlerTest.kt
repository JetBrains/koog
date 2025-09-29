package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
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
        val agentId = "test-agent-id"
        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            agentId = agentId,
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
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: Test LLM call prompt)",
            "OnLLMCallStarting (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, tools: [])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, model: openai:gpt-4o, tools: [], responses: [role: Assistant, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
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
        val strategyName = "test-strategy"

        val userPrompt = "Call the dummy tool with argument: test"
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
            "OnLLMCallStarting (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: $userPrompt}], temperature: null, tools: [dummy])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: $userPrompt}], temperature: null, model: openai:gpt-4o, tools: [${dummyTool.name}], responses: [role: Tool, message: {\"dummy\":\"test\"}])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-llm-call, input: $userPrompt, output: Call(id=null, tool=${dummyTool.name}, content={\"dummy\":\"test\"}, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={})))",
            "OnNodeExecutionStarting (run id: $runId, node: test-tool-call, input: Call(id=null, tool=${dummyTool.name}, content={\"dummy\":\"test\"}, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={})))",
            "OnToolCallStarting (run id: $runId, tool: ${dummyTool.name}, args: Args(dummy=test))",
            "OnToolCallCompleted (run id: $runId, tool: ${dummyTool.name}, args: Args(dummy=test), result: ${dummyTool.result})",
            "OnNodeExecutionCompleted (run id: $runId, node: test-tool-call, input: Call(id=null, tool=${dummyTool.name}, content={\"dummy\":\"test\"}, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={})), output: ReceivedToolResult(id=null, tool=${dummyTool.name}, content=${dummyTool.result}, result=${dummyTool.result}))",
            "OnNodeExecutionStarting (run id: $runId, node: test-node-llm-send-tool-result, input: ReceivedToolResult(id=null, tool=${dummyTool.name}, content=${dummyTool.result}, result=${dummyTool.result}))",
            "OnLLMCallStarting (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: $userPrompt, role: Tool, message: {\"dummy\":\"test\"}, role: Tool, message: ${dummyTool.result}}], temperature: null, tools: [${dummyTool.name}])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: $userPrompt, role: Tool, message: {\"dummy\":\"test\"}, role: Tool, message: ${dummyTool.result}}], temperature: null, model: openai:gpt-4o, tools: [${dummyTool.name}], responses: [role: Assistant, message: Return test result])",
            "OnNodeExecutionCompleted (run id: $runId, node: test-node-llm-send-tool-result, input: ReceivedToolResult(id=null, tool=${dummyTool.name}, content=${dummyTool.result}, result=${dummyTool.result}), output: Assistant(content=Return test result, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
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
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            toolRegistry = ToolRegistry { tool(DummyTool()) },
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
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call, input: Test LLM call prompt)",
            "OnLLMCallStarting (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, tools: [dummy])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, model: openai:gpt-4o, tools: [dummy], responses: [role: Assistant, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
            "OnNodeExecutionStarting (run id: $runId, node: test LLM call with tools, input: Test LLM call with tools prompt)",
            "OnLLMCallStarting (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt, role: Assistant, message: Default test response, role: User, message: Test LLM call with tools prompt}], temperature: null, tools: [dummy])",
            "OnLLMCallCompleted (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt, role: Assistant, message: Default test response, role: User, message: Test LLM call with tools prompt}], temperature: null, model: openai:gpt-4o, tools: [dummy], responses: [role: Assistant, message: Default test response])",
            "OnNodeExecutionCompleted (run id: $runId, node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
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

    fun AIAgentSubgraphBuilderBase<*, *>.nodeException(name: String? = null): AIAgentNodeDelegate<String, Message.Response> =
        node(name) { throw IllegalStateException("Test exception") }
}
