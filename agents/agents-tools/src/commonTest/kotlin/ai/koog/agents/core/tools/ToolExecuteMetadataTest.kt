package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalAgentToolsApi::class)
class ToolExecuteMetadataTest {

    @Serializable
    data class StringArgs(val value: String)

    private class LegacyTool : Tool<StringArgs, String>(
        argsType = typeToken<StringArgs>(),
        resultType = typeToken<String>(),
        name = "legacy-tool",
        description = "Tool implementing only the legacy execute(args)",
    ) {
        override suspend fun execute(args: StringArgs): String = "legacy:${args.value}"
    }

    private class MetadataAwareTool : Tool<StringArgs, String>(
        argsType = typeToken<StringArgs>(),
        resultType = typeToken<String>(),
        name = "metadata-aware",
        description = "Tool overriding the metadata-aware execute overload",
    ) {
        var lastMetadata: ToolCallMetadata? = null
            private set

        override suspend fun execute(args: StringArgs): String {
            lastMetadata = null
            return "legacy:${args.value}"
        }

        override suspend fun execute(args: StringArgs, metadata: ToolCallMetadata): String {
            lastMetadata = metadata
            return "metadata:${args.value}:${metadata["trace.span.id"]}"
        }
    }

    @Test
    fun testLegacyToolReceivesDefaultEmptyMetadataOverload() = runTest {
        val tool = LegacyTool()

        val direct = tool.execute(StringArgs("x"))
        val viaOverload = tool.execute(StringArgs("y"), ToolCallMetadata.of("ignored" to "value"))

        assertEquals("legacy:x", direct)
        assertEquals("legacy:y", viaOverload)
    }

    @Test
    fun testMetadataAwareToolReceivesCallerMetadata() = runTest {
        val tool = MetadataAwareTool()
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-42")

        val result = tool.execute(StringArgs("hello"), metadata)

        assertEquals("metadata:hello:span-42", result)
        assertEquals(metadata, tool.lastMetadata)
    }

    @Test
    fun testExecuteUnsafeWithMetadataRoutesToOverload() = runTest {
        val tool = MetadataAwareTool()
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-7")

        val result = tool.executeUnsafe(StringArgs("unsafe"), metadata)

        assertEquals("metadata:unsafe:span-7", result)
        assertEquals(metadata, tool.lastMetadata)
    }

    @Test
    fun testExecuteUnsafeWithoutMetadataKeepsLegacyPath() = runTest {
        val tool = LegacyTool()

        val result = tool.executeUnsafe(StringArgs("unsafe"))

        assertEquals("legacy:unsafe", result)
    }

    @Test
    fun testCallingLegacyExecuteDoesNotReachMetadataOverload() = runTest {
        val tool = MetadataAwareTool()

        val result = tool.execute(StringArgs("v"))

        assertEquals("legacy:v", result)
        assertNull(tool.lastMetadata, "Calling execute(args) must not invoke the metadata overload")
    }
}
