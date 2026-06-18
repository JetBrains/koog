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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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

    @Serializable
    sealed interface SealedOutput {
        @Serializable
        @SerialName("sealed_a")
        data class A(val payload: String) : SealedOutput

        @Serializable
        @SerialName("sealed_b")
        data class B(val number: Int) : SealedOutput
    }

    @Test
    fun `generates ToolDescriptor for sealed output with anyOf and discriminator`() {
        val finishTool = FinishTool<SealedOutput>(typeToken<SealedOutput>())

        val expectedDescriptor = ToolDescriptor(
            name = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_NAME,
            description = SubgraphWithTaskUtils.FINALIZE_SUBGRAPH_TOOL_DESCRIPTION,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "result",
                    description = "",
                    type = ToolParameterType.AnyOf(
                        types = arrayOf(
                            ToolParameterDescriptor(
                                name = "",
                                description = "",
                                type = ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            name = "type",
                                            description = "",
                                            type = ToolParameterType.Enum(arrayOf("sealed_a")),
                                        ),
                                        ToolParameterDescriptor(
                                            name = "payload",
                                            description = "",
                                            type = ToolParameterType.String,
                                        ),
                                    ),
                                    requiredProperties = listOf("type", "payload"),
                                    additionalProperties = false,
                                ),
                            ),
                            ToolParameterDescriptor(
                                name = "",
                                description = "",
                                type = ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            name = "type",
                                            description = "",
                                            type = ToolParameterType.Enum(arrayOf("sealed_b")),
                                        ),
                                        ToolParameterDescriptor(
                                            name = "number",
                                            description = "",
                                            type = ToolParameterType.Integer,
                                        ),
                                    ),
                                    requiredProperties = listOf("type", "number"),
                                    additionalProperties = false,
                                ),
                            ),
                        )
                    )
                )
            )
        )

        finishTool.descriptor shouldBe expectedDescriptor
    }

    @Test
    fun `parses sealed output branches`() = runTest {
        val finishTool = FinishTool<SealedOutput>(typeToken<SealedOutput>())

        val rawA = Json.Default.parseToJsonElement(
            "{\"result\":{\"type\":\"sealed_a\",\"payload\":\"hello\"}}"
        ).jsonObject.toKoogJSONObject()
        val rawB = Json.Default.parseToJsonElement(
            "{\"result\":{\"type\":\"sealed_b\",\"number\":42}}"
        ).jsonObject.toKoogJSONObject()

        finishTool.decodeArgs(rawA, serializer) shouldBe SealedOutput.A("hello")
        finishTool.decodeArgs(rawB, serializer) shouldBe SealedOutput.B(42)

        finishTool.encodeArgs(SealedOutput.A("hello"), serializer) shouldBe rawA
        finishTool.encodeArgs(SealedOutput.B(42), serializer) shouldBe rawB
    }
}
