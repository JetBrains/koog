package ai.koog.agents.core.environment

import ai.koog.agents.core.tools.SimpleTool
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GenericAgentEnvironmentMetadataTest {
    private val serializer = KotlinxSerializer()

    @Serializable
    private data class EchoArgs(val value: String)

    private class MetadataAwareTool : ToolBase<EchoArgs, String>(
        argsType = typeToken<EchoArgs>(),
        resultType = typeToken<String>(),
        name = "metadata_aware",
        description = "Tool that echoes a value plus a piece of metadata.",
    ) {
        val observedMetadata: MutableList<ToolCallMetadata> = mutableListOf()

        override suspend fun execute(args: EchoArgs, metadata: ToolCallMetadata): String {
            observedMetadata += metadata
            return "${args.value}::${metadata["trace.span.id"]}"
        }

        override fun encodeResultToString(result: String, serializer: JSONSerializer): String = result
    }

    private class LegacyTool : SimpleTool<EchoArgs>(
        argsType = typeToken<EchoArgs>(),
        name = "legacy",
        description = "Tool that implements only the legacy execute(args).",
    ) {
        override suspend fun execute(args: EchoArgs): String = "legacy:${args.value}"
    }

    private fun environmentWith(vararg tools: ToolBase<*, *>): GenericAgentEnvironment =
        GenericAgentEnvironment(
            agentId = "test_agent",
            logger = KotlinLogging.logger { },
            toolRegistry = ToolRegistry { tools.forEach { tool(it) } },
            serializer = serializer,
        )

    private fun callFor(toolName: String, id: String = "1", value: String = "v"): MessagePart.Tool.Call = MessagePart.Tool.Call(
        id = id,
        tool = toolName,
        args = """{"value":"$value"}""",
    )

    @Test
    fun testExecuteToolPassesMetadataToMetadataAwareTool() = runTest {
        val tool = MetadataAwareTool()
        val environment = environmentWith(tool)
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-1")

        val result = environment.executeTool(callFor("metadata_aware", value = "hello"), metadata)

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("hello::span-1", result.output)
        assertEquals(listOf(metadata), tool.observedMetadata)
    }

    @Test
    fun testExecuteToolWithoutMetadataPassesEmpty() = runTest {
        val tool = MetadataAwareTool()
        val environment = environmentWith(tool)

        val result = environment.executeTool(callFor("metadata_aware", value = "hello"))

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("hello::null", result.output)
        assertEquals(1, tool.observedMetadata.size)
        assertSame(ToolCallMetadata.EMPTY, tool.observedMetadata.single())
    }

    @Test
    fun testExecuteToolsBatchPropagatesSameMetadataToEveryCall() = runTest {
        val tool = MetadataAwareTool()
        val environment = environmentWith(tool)
        val metadata = ToolCallMetadata.of("trace.span.id" to "batch-span")

        val results = environment.executeTools(
            toolCalls = listOf(
                callFor("metadata_aware", id = "1", value = "a"),
                callFor("metadata_aware", id = "2", value = "b"),
                callFor("metadata_aware", id = "3", value = "c"),
            ),
            metadata = metadata,
        )

        assertEquals(listOf("a::batch-span", "b::batch-span", "c::batch-span"), results.map { it.output })
        assertEquals(3, tool.observedMetadata.size)
        assertTrue(tool.observedMetadata.all { it == metadata })
    }

    @Test
    fun testLegacyToolIsUnaffectedByMetadataArgument() = runTest {
        val environment = environmentWith(LegacyTool())
        val metadata = ToolCallMetadata.of("trace.span.id" to "ignored-by-legacy")

        val result = environment.executeTool(callFor("legacy", value = "x"), metadata)

        assertEquals(ToolResultKind.Success, result.resultKind)
        assertEquals("legacy:x", result.output)
    }
}
