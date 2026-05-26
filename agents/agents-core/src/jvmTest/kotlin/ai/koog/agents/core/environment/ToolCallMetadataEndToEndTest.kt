@file:OptIn(InternalAgentsApi::class)

package ai.koog.agents.core.environment

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.context.AgentTestBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.agent.tools.AgentContextAwareTool
import ai.koog.agents.core.agent.tools.agentContext
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.feature.AIAgentFeature
import ai.koog.agents.core.feature.config.FeatureConfig
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolCallMetadata
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.message.MessagePart
import ai.koog.serialization.JSONSerializer
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.typeToken
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * End-to-end validation that wires the full chain a downstream feature author would hit:
 *
 *   AIAgentFeature.install { pipeline.provideToolCallMetadata { ... } }
 *     -> AIAgentGraphPipeline.collectToolCallMetadata
 *       -> ContextualAgentEnvironment merges caller + feature metadata (caller wins) and injects AIAgentContext
 *         -> GenericAgentEnvironment routes to the registered tool
 *           -> ToolBase.execute(args, metadata) observes the merged values
 */
class ToolCallMetadataEndToEndTest : AgentTestBase() {

    @Serializable
    private data class EchoArgs(val value: String)

    private class MetadataObservingTool : ToolBase<EchoArgs, String>(
        argsType = typeToken<EchoArgs>(),
        resultType = typeToken<String>(),
        name = "metadata_aware",
        description = "Tool that captures the metadata it was invoked with.",
    ) {
        val observed: MutableList<ToolCallMetadata> = mutableListOf()

        override suspend fun execute(args: EchoArgs, metadata: ToolCallMetadata): String {
            observed += metadata
            return "echo:${args.value}"
        }

        override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
    }

    private class ContextObservingTool : AgentContextAwareTool<EchoArgs, String>(
        argsType = typeToken<EchoArgs>(),
        resultType = typeToken<String>(),
        name = "context_aware",
        description = "Tool that captures the AIAgentContext it was invoked with.",
    ) {
        val observed: MutableList<AIAgentContext> = mutableListOf()

        override suspend fun execute(args: EchoArgs, context: AIAgentContext): String {
            observed += context
            return "echo:${args.value}"
        }

        override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
    }

    private class TestFeatureConfig : FeatureConfig()

    private class TestFeature(override val key: AIAgentStorageKey<Unit>) :
        AIAgentFeature<TestFeatureConfig, Unit> {
        override fun createInitialConfig(agentConfig: AIAgentConfig): TestFeatureConfig = TestFeatureConfig()
    }

    private fun newToolCall(toolName: String = "metadata_aware", value: String = "hello"): MessagePart.Tool.Call =
        MessagePart.Tool.Call(
            id = "call-1",
            tool = toolName,
            args = """{"value":"$value"}""",
        )

    private fun environmentWith(tool: ToolBase<*, *>): GenericAgentEnvironment =
        GenericAgentEnvironment(
            agentId = "e2e-agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tool(tool) },
            serializer = KotlinxSerializer(),
        )

    @Test
    fun testFeatureContributedMetadataReachesToolExecute() = runTest {
        val tool = MetadataObservingTool()
        val generic = environmentWith(tool)
        val context = createTestContext(environment = generic)
        val feature = TestFeature(createStorageKey("trace-feature"))
        context.pipeline.provideToolCallMetadata(feature) {
            mapOf("trace.span.id" to "feature-span", "feature.key" to "f")
        }
        val contextual = ContextualAgentEnvironment(generic, context)

        val result = contextual.executeTool(newToolCall(), ToolCallMetadata.EMPTY)

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals(1, tool.observed.size)
        val seen = tool.observed.single()
        assertEquals("feature-span", seen["trace.span.id"])
        assertEquals("f", seen["feature.key"])
    }

    @Test
    fun testCallerOverridesFeatureOnKeyCollision() = runTest {
        val tool = MetadataObservingTool()
        val generic = environmentWith(tool)
        val context = createTestContext(environment = generic)
        context.pipeline.provideToolCallMetadata(TestFeature(createStorageKey("tracer"))) {
            mapOf("trace.span.id" to "feature-span", "only-feature" to "F")
        }
        val contextual = ContextualAgentEnvironment(generic, context)

        val callerMetadata = ToolCallMetadata.of(
            "trace.span.id" to "caller-span",
            "only-caller" to "C",
        )

        contextual.executeTool(newToolCall(), callerMetadata)

        val seen = tool.observed.single()
        assertEquals("caller-span", seen["trace.span.id"], "Caller must win on key collision")
        assertEquals("C", seen["only-caller"])
        assertEquals("F", seen["only-feature"])
    }

    @Test
    fun testMultipleFeaturesAccumulateInInstallationOrderThroughToTool() = runTest {
        val tool = MetadataObservingTool()
        val generic = environmentWith(tool)
        val context = createTestContext(environment = generic)
        context.pipeline.provideToolCallMetadata(TestFeature(createStorageKey("first"))) {
            mapOf("shared" to "first", "only-first" to "1")
        }
        context.pipeline.provideToolCallMetadata(TestFeature(createStorageKey("second"))) {
            mapOf("shared" to "second", "only-second" to "2")
        }
        val contextual = ContextualAgentEnvironment(generic, context)

        contextual.executeTool(newToolCall(), ToolCallMetadata.EMPTY)

        val seen = tool.observed.single()
        assertEquals("second", seen["shared"], "Later feature must overwrite earlier on key collision")
        assertEquals("1", seen["only-first"])
        assertEquals("2", seen["only-second"])
    }

    @Test
    fun testNoFeaturesAndNoCallerMetadataYieldsOnlyAgentContextAtTool() = runTest {
        val tool = MetadataObservingTool()
        val generic = environmentWith(tool)
        val context = createTestContext(environment = generic)
        val contextual = ContextualAgentEnvironment(generic, context)

        contextual.executeTool(newToolCall())

        val seen = tool.observed.single()
        assertNotNull(seen)
        assertNull(seen["trace.span.id"])
        assertSame(context, seen.agentContext)
    }

    @Test
    fun testToolBaseReadsAgentContextThroughExtensionAccessor() = runTest {
        val tool = MetadataObservingTool()
        val generic = environmentWith(tool)
        val context = createTestContext(environment = generic)
        val contextual = ContextualAgentEnvironment(generic, context)

        contextual.executeTool(newToolCall(), ToolCallMetadata.of("caller.key" to "caller-value"))

        val seen = tool.observed.single()
        assertSame(context, seen.agentContext, "Tool extending ToolBase must read the live agent context typed")
        assertEquals(context.runId, seen.agentContext?.runId)
        assertEquals("caller-value", seen["caller.key"])
    }

    @Test
    fun testAgentContextAwareToolReceivesContextTypedThroughTheFullPipeline() = runTest {
        val tool = ContextObservingTool()
        val generic = environmentWith(tool)
        val context = createTestContext(environment = generic)
        val contextual = ContextualAgentEnvironment(generic, context)

        val result = contextual.executeTool(newToolCall(toolName = "context_aware"), ToolCallMetadata.EMPTY)

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals(1, tool.observed.size)
        assertSame(context, tool.observed.single(), "AgentContextAwareTool must observe the live agent context")
    }
}
