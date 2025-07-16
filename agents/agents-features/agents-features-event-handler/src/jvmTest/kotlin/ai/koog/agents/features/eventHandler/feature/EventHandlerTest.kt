package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
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
            configureTools = { },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (agent id: test-agent-id, run id: $runId, strategy: $strategyName)",
            "OnStrategyStarted (run id: $runId, strategy: $strategyName)",
            "OnBeforeNode (run id: $runId, node: __start__, input: $agentInput)",
            "OnAfterNode (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnStrategyFinished (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentBeforeClose (agent id: test-agent-id)",
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
            configureTools = { },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (agent id: test-agent-id, run id: $runId, strategy: $strategyName)",
            "OnStrategyStarted (run id: $runId, strategy: $strategyName)",
            "OnBeforeNode (run id: $runId, node: __start__, input: $agentInput)",
            "OnAfterNode (run id: $runId, node: __start__, input: $agentInput, output: $agentInput)",
            "OnBeforeNode (run id: $runId, node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, tools: [])",
            "OnAfterLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, model: openai:gpt-4o, tools: [], responses: [role: Assistant, message: Default test response])",
            "OnAfterNode (run id: $runId, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=$ts, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
            "OnStrategyFinished (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentBeforeClose (agent id: $agentId)",
        )


        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node with tools`() = runBlocking {

        val eventsCollector = TestEventsCollector()
        val strategyName = "tracing-test-strategy"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { agentResult })
        }

        val agent = createAgent(
            strategy = strategy,
            configureTools = {
                tool(DummyTool())
            },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (agent id: test-agent-id, run id: $runId, strategy: $strategyName)",
            "OnStrategyStarted (run id: $runId, strategy: $strategyName)",
            "OnBeforeNode (run id: $runId, node: __start__, input: ${agentInput})",
            "OnAfterNode (run id: $runId, node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (run id: $runId, node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, tools: [dummy])",
            "OnAfterLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, model: openai:gpt-4o, tools: [dummy], responses: [role: Assistant, message: Default test response])",
            "OnAfterNode (run id: $runId, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
            "OnStrategyFinished (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentBeforeClose (agent id: test-agent-id)",
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
            configureTools = {
                tool(DummyTool())
            },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)
        agent.close()

        val runId = eventsCollector.runId

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (agent id: test-agent-id, run id: $runId, strategy: $strategyName)",
            "OnStrategyStarted (run id: $runId, strategy: $strategyName)",
            "OnBeforeNode (run id: $runId, node: __start__, input: ${agentInput})",
            "OnAfterNode (run id: $runId, node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (run id: $runId, node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, tools: [dummy])",
            "OnAfterLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt}], temperature: null, model: openai:gpt-4o, tools: [dummy], responses: [role: Assistant, message: Default test response])",
            "OnAfterNode (run id: $runId, node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
            "OnBeforeNode (run id: $runId, node: test LLM call with tools, input: Test LLM call with tools prompt)",
            "OnBeforeLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt, role: Assistant, message: Default test response, role: User, message: Test LLM call with tools prompt}], temperature: null, tools: [dummy])",
            "OnAfterLLMCall (run id: $runId, prompt: id: test, messages: [{role: System, message: Test system message, role: User, message: Test user message, role: Assistant, message: Test assistant response, role: User, message: Test LLM call prompt, role: Assistant, message: Default test response, role: User, message: Test LLM call with tools prompt}], temperature: null, model: openai:gpt-4o, tools: [dummy], responses: [role: Assistant, message: Default test response])",
            "OnAfterNode (run id: $runId, node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response, metaInfo=ResponseMetaInfo(timestamp=2023-01-01T00:00:00Z, totalTokensCount=null, inputTokensCount=null, outputTokensCount=null, additionalInfo={}), attachments=[], finishReason=null))",
            "OnStrategyFinished (run id: $runId, strategy: $strategyName, result: $agentResult)",
            "OnAgentFinished (agent id: test-agent-id, run id: $runId, result: $agentResult)",
            "OnAgentBeforeClose (agent id: test-agent-id)",
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
            strategy = strategy,
            configureTools = { },
            installFeatures = {
                install(EventHandler) {
                    onBeforeAgentStarted { eventContext ->
                        runId = eventContext.runId
                        collectedEvents.add("OnBeforeAgentStarted first (agent id: ${eventContext.agent.id}, strategy: ${eventContext.strategy.name})")
                    }

                    onBeforeAgentStarted { eventContext ->
                        collectedEvents.add("OnBeforeAgentStarted second (agent id: ${eventContext.agent.id}, strategy: ${eventContext.strategy.name})")
                    }

                    onAgentFinished { eventContext ->
                        collectedEvents.add("OnAgentFinished (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, result: $agentResult)")
                    }
                }
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted first (agent id: test-agent-id, strategy: $strategyName)",
            "OnBeforeAgentStarted second (agent id: test-agent-id, strategy: $strategyName)",
            "OnAgentFinished (agent id: test-agent-id, run id: $runId, result: $agentResult)",
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
            configureTools = {
                tool(DummyTool())
            },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        agent.run("Hello, world!!!")
        agent.close()
    }

    fun AIAgentSubgraphBuilderBase<*, *>.nodeException(name: String? = null): AIAgentNodeDelegate<String, Message.Response> =
        node(name) { message -> throw IllegalStateException("Test exception") }
}
