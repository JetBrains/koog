package ai.koog.prompt.executor.clients.bedrock.converse

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.Prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockCacheControl
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.clients.bedrock.util.JsonDocumentConverters
import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.services.bedrockruntime.model.Tool
import aws.sdk.kotlin.services.bedrockruntime.model.ToolChoice
import aws.sdk.kotlin.services.bedrockruntime.model.ToolConfiguration
import aws.sdk.kotlin.services.bedrockruntime.model.ToolInputSchema
import kotlinx.serialization.json.Json
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BedrockToolSchemaRequestTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun testFinalRequestPreservesNullableToolContract(streaming: Boolean) {
        fun nullable(type: ToolParameterType) = ToolParameterType.AnyOf(
            arrayOf(
                ToolParameterDescriptor("", "", ToolParameterType.Null),
                ToolParameterDescriptor("", "", type)
            )
        )

        val mode = nullable(ToolParameterType.Enum(arrayOf("setup-scan", "1h", "123", "true", "\"encoded\"")))
        val tool = ToolDescriptor(
            name = "analyze_price_trend",
            description = "Analyze price trend",
            requiredParameters = listOf(
                ToolParameterDescriptor("symbol", "Symbol", ToolParameterType.String),
                ToolParameterDescriptor("requiredMode", "Required mode", nullable(ToolParameterType.String))
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor("analysisMode", "Analysis mode", nullable(ToolParameterType.String)),
                ToolParameterDescriptor(
                    "options",
                    "Options",
                    nullable(
                        ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(
                                    "modes",
                                    "Modes",
                                    ToolParameterType.List(mode)
                                )
                            ),
                            requiredProperties = listOf("modes"),
                            additionalProperties = false
                        )
                    )
                )
            ),
            cacheControl = BedrockCacheControl.Default
        )
        val prompt = Prompt.build(
            "nullable-tool-schema",
            params = LLMParams(toolChoice = LLMParams.ToolChoice.Named(tool.name))
        ) { user("Analyze XRP-USDT") }
        val config: ToolConfiguration? = if (streaming) {
            BedrockConverseConverters.createConverseStreamRequest(
                prompt,
                BedrockModels.AnthropicClaude4Sonnet,
                listOf(tool)
            ).toolConfig
        } else {
            BedrockConverseConverters.createConverseRequest(
                prompt,
                BedrockModels.AnthropicClaude4Sonnet,
                listOf(tool)
            ).toolConfig
        }

        assertNotNull(config)
        assertEquals(tool.name, assertIs<ToolChoice.Tool>(config.toolChoice).value.name)
        assertEquals(2, config.tools.size)
        val spec = assertIs<Tool.ToolSpec>(config.tools[0]).value
        assertEquals(tool.name, spec.name)
        assertEquals(tool.description, spec.description)
        assertIs<Tool.CachePoint>(config.tools[1])
        val schema = assertIs<ToolInputSchema.Json>(spec.inputSchema).value
        assertEquals(
            Json.parseToJsonElement(
                """{
                "type":"object",
                "properties":{
                    "symbol":{"description":"Symbol","type":"string"},
                    "requiredMode":{"description":"Required mode","type":["string","null"]},
                    "analysisMode":{"description":"Analysis mode","type":["string","null"]},
                    "options":{"description":"Options","type":["object","null"],
                        "properties":{"modes":{"description":"Modes","type":"array",
                            "items":{"type":["string","null"],
                                "enum":["setup-scan","1h","123","true","encoded",null]}}},
                        "required":["modes"],"additionalProperties":false}
                },
                "required":["symbol","requiredMode"]
            }"""
            ),
            JsonDocumentConverters.convertToJsonElement(schema)
        )
    }
}
