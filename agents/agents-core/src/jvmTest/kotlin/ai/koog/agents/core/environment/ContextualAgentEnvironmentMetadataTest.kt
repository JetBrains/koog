@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.context.AgentTestBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.agent.tools.AgentContextAwareTool
import ai.koog.agents.core.agent.tools.agentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.kotlinx.toKoogJSONObject
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ContextualAgentEnvironmentMetadataTest : AgentTestBase() {

    private class CapturingEnvironment : AIAgentEnvironment {
        var lastMetadata: ToolCallMetadata? = null
            private set

        override suspend fun executeTool(toolCall: MessagePart.Tool.Call): ReceivedToolResult {
            lastMetadata = ToolCallMetadata.EMPTY
            return buildSuccessResult(toolCall)
        }

        override suspend fun executeTool(
            toolCall: MessagePart.Tool.Call,
            metadata: ToolCallMetadata,
        ): ReceivedToolResult {
            lastMetadata = metadata
            return buildSuccessResult(toolCall)
        }

        override suspend fun reportProblem(exception: Throwable) {
            throw exception
        }

        private fun buildSuccessResult(toolCall: MessagePart.Tool.Call): ReceivedToolResult = ReceivedToolResult(
            id = toolCall.id,
            tool = toolCall.tool,
            toolArgs = toolCall.argsJson.toKoogJSONObject(),
            toolDescription = null,
            output = "ok",
            resultKind = ToolResultKind.Success,
            result = JSONPrimitive("ok"),
        )
    }

    private class TestFeatureConfig : FeatureConfig()

    private class TestFeature(override val key: AIAgentStorageKey<Unit>) : AIAgentFeature<TestFeatureConfig, Unit> {
        override fun createInitialConfig(
            agentConfig: ai.koog.agents.core.agent.config.AIAgentConfig,
        ): TestFeatureConfig = TestFeatureConfig()
    }

    private fun newToolCall(): MessagePart.Tool.Call = MessagePart.Tool.Call(
        id = "tool-call-id",
        tool = "any-tool",
        args = """{"x":1}""",
    )

    @Test
    fun testCallerMetadataFlowsThroughWhenNoFeaturesRegistered() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val wrapper = ContextualAgentEnvironment(capturing, context)
        val callerMetadata = ToolCallMetadata.of("caller.key" to "caller-value")

        wrapper.executeTool(newToolCall(), callerMetadata)

        val captured = capturing.lastMetadata!!
        assertEquals("caller-value", captured["caller.key"])
    }

    @Test
    fun testFeatureContributedMetadataIsMergedWithCaller() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val pipeline = context.pipeline
        val feature = TestFeature(createStorageKey("trace-feature"))
        pipeline.provideToolCallMetadata(feature) {
            mapOf("trace.span.id" to "feature-span")
        }

        val wrapper = ContextualAgentEnvironment(capturing, context)
        val callerMetadata = ToolCallMetadata.of("caller.key" to "caller-value")

        wrapper.executeTool(newToolCall(), callerMetadata)

        val captured = capturing.lastMetadata!!
        assertEquals("feature-span", captured["trace.span.id"])
        assertEquals("caller-value", captured["caller.key"])
    }

    @Test
    fun testCallerMetadataWinsOnKeyCollision() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val pipeline = context.pipeline
        val feature = TestFeature(createStorageKey("trace-feature"))
        pipeline.provideToolCallMetadata(feature) {
            mapOf("trace.span.id" to "feature-span")
        }

        val wrapper = ContextualAgentEnvironment(capturing, context)
        val callerMetadata = ToolCallMetadata.of("trace.span.id" to "caller-span")

        wrapper.executeTool(newToolCall(), callerMetadata)

        val captured = capturing.lastMetadata!!
        assertEquals("caller-span", captured["trace.span.id"], "Caller must win on key collision")
    }

    @Test
    fun testMultipleFeaturesMergeInInstallationOrder() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val pipeline = context.pipeline

        pipeline.provideToolCallMetadata(TestFeature(createStorageKey("first"))) {
            mapOf("shared" to "first", "only-first" to "1")
        }
        pipeline.provideToolCallMetadata(TestFeature(createStorageKey("second"))) {
            mapOf("shared" to "second", "only-second" to "2")
        }

        val wrapper = ContextualAgentEnvironment(capturing, context)

        wrapper.executeTool(newToolCall(), ToolCallMetadata.EMPTY)

        val captured = capturing.lastMetadata!!
        assertEquals("second", captured["shared"], "Later feature must overwrite earlier on key collision")
        assertEquals("1", captured["only-first"])
        assertEquals("2", captured["only-second"])
    }

    @Test
    fun testNoFeaturesAndNoCallerInputCarriesOnlyAgentContext() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val wrapper = ContextualAgentEnvironment(capturing, context)

        wrapper.executeTool(newToolCall(), ToolCallMetadata.EMPTY)

        val captured = capturing.lastMetadata!!
        assertSame(context, captured.agentContext)
        assertEquals(setOf(AgentContextAwareTool.AgentContextKey), captured.keys)
    }

    @Test
    fun testLegacyExecuteToolGoesThroughMetadataPathWithEmpty() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val pipeline = context.pipeline
        pipeline.provideToolCallMetadata(TestFeature(createStorageKey("f"))) {
            mapOf("trace.span.id" to "span")
        }

        val wrapper = ContextualAgentEnvironment(capturing, context)

        // Using the single-arg overload should still fire feature contributions.
        wrapper.executeTool(newToolCall())

        val captured = capturing.lastMetadata!!
        assertEquals("span", captured["trace.span.id"])
        assertNull(captured["caller.key"])
        assertSame(context, captured.agentContext)
    }

    @Test
    fun testAgentContextIsAutoInjectedAndReadableViaTypedAccessor() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val wrapper = ContextualAgentEnvironment(capturing, context)

        wrapper.executeTool(newToolCall(), ToolCallMetadata.of("caller.key" to "caller-value"))

        val captured = capturing.lastMetadata!!
        assertSame(context, captured.agentContext, "Tool must observe the live agent context")
        assertEquals("caller-value", captured["caller.key"])
    }

    @Test
    fun testCallerCannotOverrideAgentContextThroughReservedKey() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val wrapper = ContextualAgentEnvironment(capturing, context)

        // Caller attempts to spoof the reserved key with an arbitrary value. The framework must overwrite.
        val callerMetadata = ToolCallMetadata.of(AgentContextAwareTool.AgentContextKey to "spoofed")

        wrapper.executeTool(newToolCall(), callerMetadata)

        val captured = capturing.lastMetadata!!
        assertSame(context, captured.agentContext, "Framework's live context must win over caller spoof")
    }

    @Test
    fun testFeatureCannotOverrideAgentContextThroughReservedKey() = runTest {
        val capturing = CapturingEnvironment()
        val context = createTestContext(environment = capturing)
        val pipeline = context.pipeline
        pipeline.provideToolCallMetadata(TestFeature(createStorageKey("spoofer"))) {
            mapOf(AgentContextAwareTool.AgentContextKey to "feature-spoof")
        }
        val wrapper = ContextualAgentEnvironment(capturing, context)

        wrapper.executeTool(newToolCall(), ToolCallMetadata.EMPTY)

        val captured = capturing.lastMetadata!!
        assertSame(context, captured.agentContext, "Framework's live context must win over feature spoof")
    }

    @Test
    fun testAgentContextIsNullWhenReadingMetadataNotProducedByFramework() = runTest {
        // Direct construction outside an agent run: nothing injected the context.
        val standalone = ToolCallMetadata.of("trace.span.id" to "manual")

        assertNull(standalone.agentContext)
        assertNotNull(standalone["trace.span.id"])
    }
}
