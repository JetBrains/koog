package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventHandlerTest {

    @Test
    fun `test event handler for agent without nodes and tools`() = runBlocking {

        val eventsCollector = TestEventsCollector()

        val strategyName = "tracing-test-strategy"

        val strategy = strategy(strategyName) {
            edge(nodeStart forwardTo nodeFinish transformed { "Done" })
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

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: $agentInput)",
            "OnAfterNode (node: __start__, input: $agentInput, output: $agentInput)",
            "OnStrategyFinished (strategy: $strategyName, result: Done)",
            "OnAgentFinished (strategy: $strategyName, result: Done)",
        )

        println("EXPECTED:\n${expectedEvents.joinToString("\n")}\n\nACTUAL:\n${eventsCollector.collectedEvents.joinToString("\n")}")

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node without tools`() = runBlocking {

        val eventsCollector = TestEventsCollector()

        val strategyName = "tracing-test-strategy"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { "Done" })
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

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message), User(content=Test user message), Assistant(content=Test assistant response, finishReason=null), User(content=Test LLM call prompt)], tools: [])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: Done)",
            "OnAgentFinished (strategy: $strategyName, result: Done)",
        )

        println("EXPECTED:\n${expectedEvents.joinToString("\n")}\n\nACTUAL:\n${eventsCollector.collectedEvents.joinToString("\n")}")

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler single node with tools`() = runBlocking {

        val eventsCollector = TestEventsCollector()

        val strategyName = "tracing-test-strategy"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo nodeFinish transformed { "Done" })
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

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message), User(content=Test user message), Assistant(content=Test assistant response, finishReason=null), User(content=Test LLM call prompt)], tools: [dummy])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: Done)",
            "OnAgentFinished (strategy: $strategyName, result: Done)",
        )

        println("EXPECTED:\n${expectedEvents.joinToString("\n")}\n\nACTUAL:\n${eventsCollector.collectedEvents.joinToString("\n")}")

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }

    @Test
    fun `test event handler several nodes`() = runBlocking {

        val eventsCollector = TestEventsCollector()

        val strategyName = "tracing-test-strategy"

        val strategy = strategy(strategyName) {
            val llmCallNode by nodeLLMRequest("test LLM call")
            val llmCallWithToolsNode by nodeLLMRequest("test LLM call with tools")

            edge(nodeStart forwardTo llmCallNode transformed { "Test LLM call prompt" })
            edge(llmCallNode forwardTo llmCallWithToolsNode transformed { "Test LLM call with tools prompt" })
            edge(llmCallWithToolsNode forwardTo nodeFinish transformed { "Done" })
        }

        val agent = createAgent(
            strategy = strategy,
            clock = testClock,
            configureTools = {
                tool(DummyTool())
            },
            installFeatures = {
                install(EventHandler, eventsCollector.eventHandlerFeatureConfig)
            }
        )

        val agentInput = "Hello, world!!!"
        agent.run(agentInput)

        val expectedEvents = listOf(
            "OnBeforeAgentStarted (strategy: $strategyName)",
            "OnStrategyStarted (strategy: $strategyName)",
            "OnBeforeNode (node: __start__, input: ${agentInput})",
            "OnAfterNode (node: __start__, input: ${agentInput}, output: ${agentInput})",
            "OnBeforeNode (node: test LLM call, input: Test LLM call prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message, metadata=RequestMetadata(timestamp=$ts)), User(content=Test user message, metadata=RequestMetadata(timestamp=$ts)), Assistant(content=Test assistant response, metadata=ResponseMetadata(timestamp=$ts, tokensCount=null), finishReason=null), User(content=Test LLM call prompt, metadata=RequestMetadata(timestamp=$ts))], tools: [dummy])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call, input: Test LLM call prompt, output: Assistant(content=Default test response, metadata=ResponseMetadata(timestamp=$ts, tokensCount=null), finishReason=null))",
            "OnBeforeNode (node: test LLM call with tools, input: Test LLM call with tools prompt)",
            "OnBeforeLLMCall (prompt: [System(content=Test system message, metadata=RequestMetadata(timestamp=$ts)), User(content=Test user message, metadata=RequestMetadata(timestamp=$ts)), Assistant(content=Test assistant response, metadata=ResponseMetadata(timestamp=$ts, tokensCount=null), finishReason=null), User(content=Test LLM call prompt, metadata=RequestMetadata(timestamp=$ts)), Assistant(content=Default test response, metadata=ResponseMetadata(timestamp=$ts, tokensCount=null), finishReason=null), User(content=Test LLM call with tools prompt, metadata=RequestMetadata(timestamp=$ts))], tools: [dummy])",
            "OnAfterLLMCall (responses: [Assistant: Default test response])",
            "OnAfterNode (node: test LLM call with tools, input: Test LLM call with tools prompt, output: Assistant(content=Default test response, metadata=ResponseMetadata(timestamp=$ts, tokensCount=null), finishReason=null))",
            "OnStrategyFinished (strategy: $strategyName, result: Done)",
            "OnAgentFinished (strategy: $strategyName, result: Done)",
        )

        println("EXPECTED:\n${expectedEvents.joinToString("\n")}\n\nACTUAL:\n${eventsCollector.collectedEvents.joinToString("\n")}")

        assertEquals(expectedEvents.size, eventsCollector.size)
        assertContentEquals(expectedEvents, eventsCollector.collectedEvents)
    }
}
