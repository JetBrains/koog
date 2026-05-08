package ai.koog.agents.core.tools

import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.serialization.typeToken
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    private class BothOverloadsTool : Tool<StringArgs, String>(
        argsType = typeToken<StringArgs>(),
        resultType = typeToken<String>(),
        name = "both-overloads",
        description = "Tool overriding both execute(args) and execute(args, metadata)",
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

    private class MetadataOnlyTool : Tool<StringArgs, String>(
        argsType = typeToken<StringArgs>(),
        resultType = typeToken<String>(),
        name = "metadata-only",
        description = "Tool overriding only execute(args, metadata)",
    ) {
        var lastMetadata: ToolCallMetadata? = null
            private set

        override suspend fun execute(args: StringArgs, metadata: ToolCallMetadata): String {
            lastMetadata = metadata
            return "metadata:${args.value}:${metadata["trace.span.id"]}"
        }
    }

    private class NeitherOverridesTool : Tool<StringArgs, String>(
        argsType = typeToken<StringArgs>(),
        resultType = typeToken<String>(),
        name = "neither-overrides",
        description = "Tool that overrides neither execute overload",
    )

    @Test
    fun testLegacyToolReceivesDefaultEmptyMetadataOverload() = runTest {
        val tool = LegacyTool()

        val direct = tool.execute(StringArgs("x"))
        val viaOverload = tool.execute(StringArgs("y"), ToolCallMetadata.of("ignored" to "value"))

        assertEquals("legacy:x", direct)
        assertEquals("legacy:y", viaOverload)
    }

    @Test
    fun testBothOverloadsToolReceivesCallerMetadata() = runTest {
        val tool = BothOverloadsTool()
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-42")

        val result = tool.execute(StringArgs("hello"), metadata)

        assertEquals("metadata:hello:span-42", result)
        assertEquals(metadata, tool.lastMetadata)
    }

    @Test
    fun testExecuteUnsafeWithMetadataRoutesToOverload() = runTest {
        val tool = BothOverloadsTool()
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
    fun testBothOverloadsRouteEachEntryToItsOwnOverride() = runTest {
        val tool = BothOverloadsTool()

        val result = tool.execute(StringArgs("v"))

        assertEquals("legacy:v", result)
        assertNull(tool.lastMetadata, "Calling execute(args) on a tool that overrides it must not invoke the metadata overload")
    }

    @Test
    fun testMetadataOnlyToolReceivesCallerMetadata() = runTest {
        val tool = MetadataOnlyTool()
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-1")

        val result = tool.execute(StringArgs("hello"), metadata)

        assertEquals("metadata:hello:span-1", result)
        assertEquals(metadata, tool.lastMetadata)
    }

    @Test
    fun testMetadataOnlyToolReceivesEmptyWhenInvokedWithoutMetadata() = runTest {
        val tool = MetadataOnlyTool()

        val result = tool.execute(StringArgs("hello"))

        assertEquals("metadata:hello:null", result)
        assertSame(
            ToolCallMetadata.EMPTY,
            tool.lastMetadata,
            "execute(args) must route to the metadata override with ToolCallMetadata.EMPTY",
        )
    }

    @Test
    fun testMetadataOnlyToolViaExecuteUnsafeWithoutMetadataReceivesEmpty() = runTest {
        val tool = MetadataOnlyTool()

        val result = tool.executeUnsafe(StringArgs("unsafe"))

        assertEquals("metadata:unsafe:null", result)
        assertSame(
            ToolCallMetadata.EMPTY,
            tool.lastMetadata,
            "executeUnsafe(args) must route to the metadata override with ToolCallMetadata.EMPTY",
        )
    }

    @Test
    fun testMetadataOnlyToolViaExecuteUnsafeWithMetadataReceivesIt() = runTest {
        val tool = MetadataOnlyTool()
        val metadata = ToolCallMetadata.of("trace.span.id" to "span-9")

        val result = tool.executeUnsafe(StringArgs("unsafe"), metadata)

        assertEquals("metadata:unsafe:span-9", result)
        assertEquals(metadata, tool.lastMetadata)
    }

    @Test
    fun testToolWithoutOverridesThrowsClearErrorViaExecuteArgs() = runTest {
        val tool = NeitherOverridesTool()

        val error = assertFailsWith<NotImplementedError> {
            tool.execute(StringArgs("v"))
        }

        val message = error.message.orEmpty()
        assertTrue("neither-overrides" in message, "message must name the tool, was: $message")
        assertTrue("execute(args)" in message, "message must reference execute(args), was: $message")
        assertTrue("execute(args, metadata)" in message, "message must reference execute(args, metadata), was: $message")
    }

    @Test
    fun testToolWithoutOverridesThrowsClearErrorViaExecuteArgsMetadata() = runTest {
        val tool = NeitherOverridesTool()

        val error = assertFailsWith<NotImplementedError> {
            tool.execute(StringArgs("v"), ToolCallMetadata.of("trace.span.id" to "x"))
        }

        val message = error.message.orEmpty()
        assertTrue("neither-overrides" in message, "message must name the tool, was: $message")
    }

    @Test
    fun testToolWithoutOverridesThrowsClearErrorViaExecuteUnsafeArgs() = runTest {
        val tool = NeitherOverridesTool()

        assertFailsWith<NotImplementedError> {
            tool.executeUnsafe(StringArgs("v"))
        }
    }

    @Test
    fun testToolWithoutOverridesThrowsClearErrorViaExecuteUnsafeArgsMetadata() = runTest {
        val tool = NeitherOverridesTool()

        assertFailsWith<NotImplementedError> {
            tool.executeUnsafe(StringArgs("v"), ToolCallMetadata.EMPTY)
        }
    }
}
