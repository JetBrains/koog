package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.asUserMessage
import ai.koog.agents.core.dsl.extension.nodeExecuteToolsAndGetResults
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.feature.handler.agent.AgentClosingContext
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentExecutionFailedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionCompletedContext
import ai.koog.agents.core.feature.handler.node.NodeExecutionFailedContext
import ai.koog.agents.core.feature.handler.strategy.StrategyCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.serialization.kotlinx.KotlinxSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Verifies that the `duration` parameter wired through pipeline events captures the right window:
 *
 * - Non-negative after work runs.
 * - The completion/non-streaming-failure events always carry a measurement (non-null `Duration`).
 * - The three "may-be-unmeasured" events expose `Duration?` and report `null` when the operation
 *   failed before its mark could be captured (e.g., an `onAgentStarting` handler threw, or a cold
 *   streaming flow was cancelled before its body began).
 * - The aggregate-scope duration (`onAgentClosing`) is at least as large as nested-scope durations
 *   that completed before it fired.
 *
 * The implementation uses `TimeSource.Monotonic`, which reads real wall-clock nanoseconds — `runTest`
 * virtual-time advancement does not affect it. The assertions therefore stick to invariants that hold
 * regardless of how fast the host machine is.
 */
class PipelineDurationTest {

    private val serializer = KotlinxSerializer()

    private class DurationCollector {
        var agentCompleted: AgentCompletedContext? = null
        var agentExecutionFailed: AgentExecutionFailedContext? = null
        var agentClosing: AgentClosingContext? = null
        var strategyCompleted: StrategyCompletedContext? = null
        val nodesCompleted = mutableListOf<NodeExecutionCompletedContext>()
        val nodesFailed = mutableListOf<NodeExecutionFailedContext>()
        val subgraphsCompleted = mutableListOf<SubgraphExecutionCompletedContext>()
        val llmCallsCompleted = mutableListOf<LLMCallCompletedContext>()
        val toolCallsCompleted = mutableListOf<ToolCallCompletedContext>()

        val config: EventHandlerConfig.() -> Unit = {
            onAgentCompleted { agentCompleted = it }
            onAgentExecutionFailed { agentExecutionFailed = it }
            onAgentClosing { agentClosing = it }
            onStrategyCompleted { strategyCompleted = it }
            onNodeExecutionCompleted { nodesCompleted += it }
            onNodeExecutionFailed { nodesFailed += it }
            onSubgraphExecutionCompleted { subgraphsCompleted += it }
            onLLMCallCompleted { llmCallsCompleted += it }
            onToolCallCompleted { toolCallsCompleted += it }
        }
    }

    @Test
    fun `successful run populates duration on every completion event`() = runTest {
        val collector = DurationCollector()
        val strategyName = "duration-test-strategy"
        val agentInput = "Hello"
        val agentResult = "Done"

        val strategy = strategy<String, String>(strategyName) {
            val workNode by node<String, String>("work-node") { input -> input }
            edge(nodeStart forwardTo workNode)
            edge(workNode forwardTo nodeFinish transformed { agentResult })
        }

        createAgent(
            strategy = strategy,
            installFeatures = { install(EventHandler, collector.config) }
        ).run(agentInput, null)

        val agentCompleted = assertNotNull(collector.agentCompleted, "onAgentCompleted should have fired")
        val strategyCompleted = assertNotNull(collector.strategyCompleted, "onStrategyCompleted should have fired")
        val agentClosing = assertNotNull(collector.agentClosing, "onAgentClosing should have fired")

        // Each completion event reports a non-negative duration.
        assertTrue(agentCompleted.duration >= Duration.ZERO, "agentCompleted.duration: ${agentCompleted.duration}")
        assertTrue(strategyCompleted.duration >= Duration.ZERO, "strategyCompleted.duration: ${strategyCompleted.duration}")

        val workNodeRun = collector.nodesCompleted.firstOrNull { it.node.name == "work-node" }
        assertNotNull(workNodeRun, "work-node should have completed")
        assertTrue(workNodeRun.duration >= Duration.ZERO, "node duration: ${workNodeRun.duration}")

        // Closing measures the broader session — it fires AFTER onAgentCompleted's mark stops accruing,
        // and uses a mark captured BEFORE prepareFeatures. So closing.duration must be ≥ agentCompleted.duration.
        assertTrue(
            agentClosing.duration >= agentCompleted.duration,
            "closing.duration ${agentClosing.duration} should be >= completed.duration ${agentCompleted.duration}"
        )
    }

    @Test
    fun `failure during strategy execution carries a non-null duration`() = runTest {
        val collector = DurationCollector()

        val strategy = strategy<String, String>("failing-strategy") {
            val failNode by node<String, String>("fail-node") { error("boom") }
            edge(nodeStart forwardTo failNode)
            edge(failNode forwardTo nodeFinish)
        }

        val ex = runCatching {
            createAgent(
                strategy = strategy,
                installFeatures = { install(EventHandler, collector.config) }
            ).run("anything", null)
        }.exceptionOrNull()

        assertNotNull(ex, "Agent should propagate the strategy failure")
        val failed = assertNotNull(collector.agentExecutionFailed, "onAgentExecutionFailed should have fired")
        // The mark was captured AFTER onAgentStarting, before strategy execution. By the time the failure event
        // fires, the strategy has run (and failed), so the duration must be measured — not the null sentinel.
        val failedDuration = assertNotNull(
            failed.duration,
            "failed.duration should be measured when the failure happened during strategy execution"
        )
        assertTrue(failedDuration >= Duration.ZERO, "failed.duration should be non-negative (was $failedDuration)")

        val nodeFailed = collector.nodesFailed.firstOrNull { it.node.name == "fail-node" }
        assertNotNull(nodeFailed, "fail-node should have produced an onNodeExecutionFailed event")
        assertTrue(nodeFailed.duration >= Duration.ZERO, "node-failed duration: ${nodeFailed.duration}")

        val closing = assertNotNull(collector.agentClosing, "onAgentClosing must fire on the failure path")
        assertTrue(closing.duration >= Duration.ZERO, "closing.duration: ${closing.duration}")
    }

    @Test
    fun `agent failure before strategy executes reports null duration`() = runTest {
        val collector = DurationCollector()

        val strategy = strategy<String, String>("never-runs") {
            edge(nodeStart forwardTo nodeFinish transformed { "unused" })
        }

        val ex = runCatching {
            createAgent(
                strategy = strategy,
                installFeatures = {
                    // Throw from onAgentStarting — this fires BEFORE the per-run mark is captured.
                    install(EventHandler) {
                        collector.config(this)
                        onAgentStarting { error("blocked at startup") }
                    }
                }
            ).run("anything", null)
        }.exceptionOrNull()

        assertNotNull(ex, "the startup error must propagate")
        val failed = assertNotNull(collector.agentExecutionFailed, "failure event must still fire")

        // Documented contract: AgentExecutionFailedContext.duration is `null` when the failure originated
        // before the per-run mark was captured. This is the consumer's signal for "execution never began".
        assertNull(
            failed.duration,
            "duration should be null when failure occurred before strategy mark (was ${failed.duration})"
        )
    }

    @Test
    fun `tool call duration covers the full tool dispatch`() = runTest {
        val collector = DurationCollector()
        val dummyTool = DummyTool()
        val toolRegistry = ToolRegistry { tool(dummyTool) }

        val userPrompt = "Call the dummy tool with argument: test"
        val mockResponse = "Return test result"

        val strategy = strategy<String, String>("tool-test-strategy") {
            val send by nodeLLMRequest("send")
            val exec by nodeExecuteToolsAndGetResults("exec")
            edge(nodeStart forwardTo send asUserMessage { it })
            edge(send forwardTo exec onToolCalls { true })
            edge(send forwardTo nodeFinish onTextMessage { true })
            edge(exec forwardTo nodeFinish transformed { mockResponse })
        }

        val executor = getMockExecutor(serializer, clock = testClock) {
            mockLLMToolCall(dummyTool, DummyTool.Args("test")) onRequestEquals userPrompt
            mockLLMAnswer(mockResponse) onRequestContains dummyTool.result
        }

        createAgent(
            strategy = strategy,
            executor = executor,
            userPrompt = userPrompt,
            toolRegistry = toolRegistry,
            installFeatures = { install(EventHandler, collector.config) }
        ).run(userPrompt, null)

        val toolCall = collector.toolCallsCompleted.firstOrNull { it.toolName == dummyTool.name }
        assertNotNull(toolCall, "tool call must have completed")
        assertTrue(toolCall.duration >= Duration.ZERO, "tool call duration should be non-negative")
        // We can't easily assert a specific magnitude without a delay in the tool. The point is just that we
        // observed the field populated and it's a valid Duration.

        val llmCall = collector.llmCallsCompleted.firstOrNull()
        assertNotNull(llmCall, "at least one LLM call must have completed")
        assertTrue(llmCall.duration >= Duration.ZERO, "llm call duration should be non-negative")
    }
}
