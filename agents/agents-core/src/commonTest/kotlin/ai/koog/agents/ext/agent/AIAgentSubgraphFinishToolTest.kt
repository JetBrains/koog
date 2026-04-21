package ai.koog.agents.ext.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.kotlinx.KotlinxSerializer
import ai.koog.serialization.kotlinx.toKoogJSONObject
import ai.koog.serialization.typeToken
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test

@OptIn(InternalAgentToolsApi::class)
class AIAgentSubgraphFinishToolTest {
    val serializer = KotlinxSerializer()

    @Serializable
    @LLMDescription("Test output description")
    data class TestOutput(
        val foo: String
    )

    @Test
    fun `generates ToolDescriptor for complex output`() {
        val finishTool = FinishTool<TestOutput>(typeToken<TestOutput>())

        val expectedDescriptor = ToolDescriptor(
            name = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_NAME,
            description = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_DESCRIPTION,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "result",
                    description = "Test output description",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "foo",
                                description = "",
                                type = ToolParameterType.String
                            )
                        ),
                        requiredProperties = listOf("foo"),
                        additionalProperties = false
                    )
                )
            )
        )

        finishTool.descriptor shouldBe expectedDescriptor
    }

    @Test
    fun `parses complex output`() = runTest {
        val finishTool = FinishTool<TestOutput>(typeToken<TestOutput>())

        val result = TestOutput("bar")
        val resultSerialized = buildJsonObject {
            putJsonObject("result") {
                put("foo", "bar")
            }
        }.toKoogJSONObject()

        finishTool.decodeArgs(resultSerialized, serializer) shouldBe result
        finishTool.encodeArgs(result, serializer) shouldBe resultSerialized

        finishTool.decodeResult(resultSerialized, serializer) shouldBe result
        finishTool.encodeResult(result, serializer) shouldBe resultSerialized
    }

    @Test
    fun `generates ToolDescriptor for primitive output`() = runTest {
        val finishTool = FinishTool<String>(typeToken<String>())

        val expectedDescriptor = ToolDescriptor(
            name = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_NAME,
            description = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_DESCRIPTION,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "result",
                    description = "",
                    type = ToolParameterType.String
                )
            )
        )

        finishTool.descriptor shouldBe expectedDescriptor
    }
}
