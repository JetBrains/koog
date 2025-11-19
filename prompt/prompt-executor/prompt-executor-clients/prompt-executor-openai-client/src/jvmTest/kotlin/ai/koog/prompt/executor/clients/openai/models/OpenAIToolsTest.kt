package ai.koog.prompt.executor.clients.openai.models

import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class OpenAIToolsTest {

    @Test
    fun `test OpenAIResponsesTool_Function serialization`() =
        runWithBothJsonConfigurations("Function tool serialization") { json ->
            val functionTool = OpenAIResponsesTool.Function(
                name = "get_weather",
                parameters = buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put(
                        "properties",
                        buildJsonObject {
                            put(
                                "location",
                                buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("description", JsonPrimitive("The city name"))
                                }
                            )
                        }
                    )
                    put(
                        "required",
                        buildJsonArray {
                            add(JsonPrimitive("location"))
                        }
                    )
                },
                strict = true,
                description = "Get current weather for a location"
            )

            json.encodeToString(functionTool) shouldEqualJson """
            {
                "name": "get_weather",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "location": {
                            "type": "string",
                            "description": "The city name"
                        }
                    },
                    "required": ["location"]
                },
                "strict": true,
                "description": "Get current weather for a location"
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesTool.Function>(
                json.encodeToString(functionTool)
            ).shouldNotBeNull {
                name shouldBe "get_weather"
                description shouldBe "Get current weather for a location"
                strict shouldBe true
                parameters["type"]?.jsonPrimitive?.content shouldBe "object"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_FileSearch serialization`() =
        runWithBothJsonConfigurations("FileSearch tool serialization") { json ->
            val fileSearchTool = OpenAIResponsesTool.FileSearch(
                vectorStoreIds = listOf("vs_123", "vs_456"),
                filters = buildJsonObject {
                    put("category", JsonPrimitive("documents"))
                },
                maxNumResults = 10,
                rankingOptions = buildJsonObject {
                    put("algorithm", JsonPrimitive("similarity"))
                }
            )

            json.encodeToString(fileSearchTool) shouldEqualJson """
            {
                "vectorStoreIds": ["vs_123", "vs_456"],
                "filters": {"category": "documents"},
                "maxNumResults": 10,
                "rankingOptions": {"algorithm": "similarity"}
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesTool.FileSearch>(
                json.encodeToString(fileSearchTool)
            ).shouldNotBeNull {
                vectorStoreIds shouldHaveSize 2
                vectorStoreIds shouldBe listOf("vs_123", "vs_456")
                maxNumResults shouldBe 10
                filters?.get("category")?.jsonPrimitive?.content shouldBe "documents"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_WebSearchPreview serialization`() =
        runWithBothJsonConfigurations("WebSearchPreview tool serialization") { json ->
            val webSearchTool = OpenAIResponsesTool.WebSearchPreview(
                searchContextSize = "medium",
                userLocation = OpenAIResponsesTool.WebSearchPreview.UserLocation(
                    city = "San Francisco",
                    country = "US",
                    region = "California",
                    timezone = "America/Los_Angeles"
                )
            )

            json.decodeFromString<OpenAIResponsesTool.WebSearchPreview>(
                json.encodeToString(webSearchTool)
            ).shouldNotBeNull {
                searchContextSize shouldBe "medium"
                userLocation.shouldNotBeNull {
                    city shouldBe "San Francisco"
                    country shouldBe "US"
                    region shouldBe "California"
                    timezone shouldBe "America/Los_Angeles"
                    type shouldBe "approximate"
                }
            }
        }

    @Test
    fun `test OpenAIResponsesTool_ComputerUsePreview serialization`() =
        runWithBothJsonConfigurations("ComputerUsePreview tool serialization") { json ->
            val computerUseTool = OpenAIResponsesTool.ComputerUsePreview(
                displayHeight = 1080,
                displayWidth = 1920,
                environment = "desktop"
            )

            json.encodeToString(computerUseTool) shouldEqualJson """
            {
                "displayHeight": 1080,
                "displayWidth": 1920,
                "environment": "desktop"
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesTool.ComputerUsePreview>(
                json.encodeToString(computerUseTool)
            ).shouldNotBeNull {
                displayHeight shouldBe 1080
                displayWidth shouldBe 1920
                environment shouldBe "desktop"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_McpTool serialization`() =
        runWithBothJsonConfigurations("McpTool serialization") { json ->
            val mcpTool = OpenAIResponsesTool.McpTool(
                serverLabel = "my_mcp_server",
                serverUrl = "https://mcp.example.com",
                allowedTools = listOf("search", "analyze"),
                headers = buildJsonObject {
                    put("Authorization", JsonPrimitive("Bearer token123"))
                },
                requireApproval = "all",
                serverDescription = "External MCP server for data analysis"
            )

            json.encodeToString(mcpTool) shouldEqualJson """
            {
                "serverLabel": "my_mcp_server",
                "serverUrl": "https://mcp.example.com",
                "allowedTools": ["search", "analyze"],
                "headers": {"Authorization": "Bearer token123"},
                "requireApproval": "all",
                "serverDescription": "External MCP server for data analysis"
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesTool.McpTool>(
                json.encodeToString(mcpTool)
            ).shouldNotBeNull {
                serverLabel shouldBe "my_mcp_server"
                serverUrl shouldBe "https://mcp.example.com"
                allowedTools shouldBe listOf("search", "analyze")
                headers?.get("Authorization")?.jsonPrimitive?.content shouldBe "Bearer token123"
                requireApproval shouldBe "all"
                serverDescription shouldBe "External MCP server for data analysis"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_CodeInterpreter serialization`() =
        runWithBothJsonConfigurations("CodeInterpreter tool serialization") { json ->
            val codeInterpreter = OpenAIResponsesTool.CodeInterpreter("container_abc123")

            json.encodeToString(codeInterpreter) shouldEqualJson """
            {
                "container": "container_abc123"
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesTool.CodeInterpreter>(
                json.encodeToString(codeInterpreter)
            ).shouldNotBeNull {
                container shouldBe "container_abc123"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_ImageGenerationTool serialization`() =
        runWithBothJsonConfigurations("ImageGenerationTool serialization") { json ->
            val imageGenTool = OpenAIResponsesTool.ImageGenerationTool(
                background = "transparent",
                inputFidelity = "high",
                inputImageMask = buildJsonObject {
                    put("image_url", JsonPrimitive("https://example.com/mask.png"))
                },
                model = "gpt-image-1",
                moderation = "auto",
                outputCompression = 95,
                outputFormat = "png",
                partialImages = 2,
                quality = "high",
                size = "1024x1024"
            )

            json.decodeFromString<OpenAIResponsesTool.ImageGenerationTool>(
                json.encodeToString(imageGenTool)
            ).shouldNotBeNull {
                background shouldBe "transparent"
                inputFidelity shouldBe "high"
                model shouldBe "gpt-image-1"
                moderation shouldBe "auto"
                outputCompression shouldBe 95
                outputFormat shouldBe "png"
                partialImages shouldBe 2
                quality shouldBe "high"
                size shouldBe "1024x1024"
                inputImageMask?.get("image_url")?.jsonPrimitive?.content shouldBe "https://example.com/mask.png"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_LocalShellTool serialization`() =
        runWithBothJsonConfigurations("LocalShellTool serialization") { json ->
            val localShellTool = OpenAIResponsesTool.LocalShellTool()

            json.encodeToString(localShellTool) shouldEqualJson """
            {
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesTool.LocalShellTool>(
                json.encodeToString(localShellTool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_CustomTool with text format`() =
        runWithBothJsonConfigurations("CustomTool text format") { json ->
            val textFormatTool = OpenAIResponsesTool.CustomTool(
                name = "text_processor",
                description = "Process text input",
                format = OpenAIResponsesTool.CustomTool.Format.Text()
            )

            json.decodeFromString<OpenAIResponsesTool.CustomTool>(
                json.encodeToString(textFormatTool)
            ).shouldNotBeNull {
                name shouldBe "text_processor"
                description shouldBe "Process text input"
                format?.javaClass shouldBe OpenAIResponsesTool.CustomTool.Format.Text::class.java
            }
        }

    @Test
    fun `test OpenAIResponsesTool_CustomTool with grammar format`() =
        runWithBothJsonConfigurations("CustomTool grammar format") { json ->
            val grammarFormatTool = OpenAIResponsesTool.CustomTool(
                name = "grammar_parser",
                description = "Parse with custom grammar",
                format = OpenAIResponsesTool.CustomTool.Format.Grammar(
                    definition = "start: expr+",
                    syntax = "lark"
                )
            )

            json.decodeFromString<OpenAIResponsesTool.CustomTool>(
                json.encodeToString(grammarFormatTool)
            ).shouldNotBeNull {
                name shouldBe "grammar_parser"
                description shouldBe "Parse with custom grammar"
                val grammarFormat = format as OpenAIResponsesTool.CustomTool.Format.Grammar
                grammarFormat.definition shouldBe "start: expr+"
                grammarFormat.syntax shouldBe "lark"
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoice_Mode auto serialization`() =
        runWithBothJsonConfigurations("ToolChoice Mode auto") { json ->
            val autoChoice = OpenAIResponsesToolChoice.Mode("auto")

            json.decodeFromJsonElement(
                OpenAIResponsesToolChoiceSerializer,
                JsonPrimitive("auto")
            ) shouldBe autoChoice
        }

    @Test
    fun `test OpenAIResponsesToolChoice_Mode none serialization`() =
        runWithBothJsonConfigurations("ToolChoice Mode none") { json ->
            val noneChoice = OpenAIResponsesToolChoice.Mode("none")

            json.decodeFromJsonElement(
                OpenAIResponsesToolChoiceSerializer,
                JsonPrimitive("none")
            ) shouldBe noneChoice
        }

    @Test
    fun `test OpenAIResponsesToolChoice_Mode required serialization`() =
        runWithBothJsonConfigurations("ToolChoice Mode required") { json ->
            val requiredChoice = OpenAIResponsesToolChoice.Mode("required")

            json.decodeFromJsonElement(
                OpenAIResponsesToolChoiceSerializer,
                JsonPrimitive("required")
            ) shouldBe requiredChoice
        }

    @Test
    fun `test OpenAIResponsesToolChoice_AllowedTools serialization`() =
        runWithBothJsonConfigurations("ToolChoice AllowedTools serialization") { json ->
            val allowedTools = OpenAIResponsesToolChoice.AllowedTools(
                mode = "auto",
                tools = listOf(
                    buildJsonObject {
                        put("type", JsonPrimitive("function"))
                        put("name", JsonPrimitive("get_weather"))
                    },
                    buildJsonObject {
                        put("type", JsonPrimitive("mcp"))
                        put("server_label", JsonPrimitive("deepwiki"))
                    }
                )
            )

            json.encodeToString(OpenAIResponsesToolChoiceSerializer, allowedTools) shouldEqualJson """
            {
                "type": "allowed_tools",
                "mode": "auto",
                "tools": [
                    {"type": "function", "name": "get_weather"},
                    {"type": "mcp", "server_label": "deepwiki"}
                ]
            }
            """.trimIndent()

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, allowedTools))
                ) as OpenAIResponsesToolChoice.AllowedTools
                ).shouldNotBeNull {
                mode shouldBe "auto"
                tools shouldHaveSize 2
                type shouldBe "allowed_tools"
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoice_HostedTool file_search serialization`() =
        runWithBothJsonConfigurations("ToolChoice HostedTool file_search") { json ->
            val tool = OpenAIResponsesToolChoice.HostedTool("file_search")

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, tool))
                ) as OpenAIResponsesToolChoice.HostedTool
                ).type shouldBe "file_search"
        }

    @Test
    fun `test OpenAIResponsesToolChoice_HostedTool web_search_preview serialization`() =
        runWithBothJsonConfigurations("ToolChoice HostedTool web_search_preview") { json ->
            val tool = OpenAIResponsesToolChoice.HostedTool("web_search_preview")

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, tool))
                ) as OpenAIResponsesToolChoice.HostedTool
                ).type shouldBe "web_search_preview"
        }

    @Test
    fun `test OpenAIResponsesToolChoice_HostedTool computer_use_preview serialization`() =
        runWithBothJsonConfigurations("ToolChoice HostedTool computer_use_preview") { json ->
            val tool = OpenAIResponsesToolChoice.HostedTool("computer_use_preview")

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, tool))
                ) as OpenAIResponsesToolChoice.HostedTool
                ).type shouldBe "computer_use_preview"
        }

    @Test
    fun `test OpenAIResponsesToolChoice_HostedTool code_interpreter serialization`() =
        runWithBothJsonConfigurations("ToolChoice HostedTool code_interpreter") { json ->
            val tool = OpenAIResponsesToolChoice.HostedTool("code_interpreter")

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, tool))
                ) as OpenAIResponsesToolChoice.HostedTool
                ).type shouldBe "code_interpreter"
        }

    @Test
    fun `test OpenAIResponsesToolChoice_HostedTool image_generation serialization`() =
        runWithBothJsonConfigurations("ToolChoice HostedTool image_generation") { json ->
            val tool = OpenAIResponsesToolChoice.HostedTool("image_generation")

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, tool))
                ) as OpenAIResponsesToolChoice.HostedTool
                ).type shouldBe "image_generation"
        }

    @Test
    fun `test OpenAIResponsesToolChoice_FunctionTool serialization`() =
        runWithBothJsonConfigurations("ToolChoice FunctionTool serialization") { json ->
            val functionTool = OpenAIResponsesToolChoice.FunctionTool("calculate_sum")

            json.encodeToString(OpenAIResponsesToolChoiceSerializer, functionTool) shouldEqualJson """
            {
                "type": "function",
                "name": "calculate_sum"
            }
            """.trimIndent()

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, functionTool))
                ) as OpenAIResponsesToolChoice.FunctionTool
                ).shouldNotBeNull {
                name shouldBe "calculate_sum"
                type shouldBe "function"
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoice_McpTool with name serialization`() =
        runWithBothJsonConfigurations("ToolChoice McpTool with name") { json ->
            val mcpToolWithName = OpenAIResponsesToolChoice.McpTool(
                serverLabel = "my_server",
                name = "search_tool"
            )

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, mcpToolWithName))
                ) as OpenAIResponsesToolChoice.McpTool
                ).shouldNotBeNull {
                serverLabel shouldBe "my_server"
                name shouldBe "search_tool"
                type shouldBe "mcp"
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoice_McpTool without name serialization`() =
        runWithBothJsonConfigurations("ToolChoice McpTool without name") { json ->
            val mcpToolWithoutName = OpenAIResponsesToolChoice.McpTool(
                serverLabel = "another_server"
            )

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(
                        json.encodeToString(
                            OpenAIResponsesToolChoiceSerializer,
                            mcpToolWithoutName
                        )
                    )
                ) as OpenAIResponsesToolChoice.McpTool
                ).shouldNotBeNull {
                serverLabel shouldBe "another_server"
                name shouldBe null
                type shouldBe "mcp"
            }
        }

    @Test
    fun `test OpenAIResponsesToolChoice_CustomTool serialization`() =
        runWithBothJsonConfigurations("ToolChoice CustomTool serialization") { json ->
            val customTool = OpenAIResponsesToolChoice.CustomTool("data_transformer")

            json.encodeToString(OpenAIResponsesToolChoiceSerializer, customTool) shouldEqualJson """
            {
                "type": "custom",
                "name": "data_transformer"
            }
            """.trimIndent()

            (
                json.decodeFromJsonElement(
                    OpenAIResponsesToolChoiceSerializer,
                    json.parseToJsonElement(json.encodeToString(OpenAIResponsesToolChoiceSerializer, customTool))
                ) as OpenAIResponsesToolChoice.CustomTool
                ).shouldNotBeNull {
                name shouldBe "data_transformer"
                type shouldBe "custom"
            }
        }

    @Test
    fun `test OpenAIResponsesTool_Function round trip serialization`() =
        runWithBothJsonConfigurations("Function tool round trip") { json ->
            val tool = OpenAIResponsesTool.Function(
                name = "test_func",
                parameters = buildJsonObject { put("type", JsonPrimitive("object")) }
            )

            json.decodeFromString<OpenAIResponsesTool.Function>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_FileSearch round trip serialization`() =
        runWithBothJsonConfigurations("FileSearch tool round trip") { json ->
            val tool = OpenAIResponsesTool.FileSearch(vectorStoreIds = listOf("vs_1"))

            json.decodeFromString<OpenAIResponsesTool.FileSearch>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_WebSearchPreview round trip serialization`() =
        runWithBothJsonConfigurations("WebSearchPreview tool round trip") { json ->
            val tool = OpenAIResponsesTool.WebSearchPreview()

            json.decodeFromString<OpenAIResponsesTool.WebSearchPreview>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_ComputerUsePreview round trip serialization`() =
        runWithBothJsonConfigurations("ComputerUsePreview tool round trip") { json ->
            val tool = OpenAIResponsesTool.ComputerUsePreview(1080, 1920, "desktop")

            json.decodeFromString<OpenAIResponsesTool.ComputerUsePreview>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_McpTool round trip serialization`() =
        runWithBothJsonConfigurations("McpTool round trip") { json ->
            val tool = OpenAIResponsesTool.McpTool("server1", "https://server1.com")

            json.decodeFromString<OpenAIResponsesTool.McpTool>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_CodeInterpreter round trip serialization`() =
        runWithBothJsonConfigurations("CodeInterpreter round trip") { json ->
            val tool = OpenAIResponsesTool.CodeInterpreter("container1")

            json.decodeFromString<OpenAIResponsesTool.CodeInterpreter>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_ImageGenerationTool round trip serialization`() =
        runWithBothJsonConfigurations("ImageGenerationTool round trip") { json ->
            val tool = OpenAIResponsesTool.ImageGenerationTool()

            json.decodeFromString<OpenAIResponsesTool.ImageGenerationTool>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_LocalShellTool round trip serialization`() =
        runWithBothJsonConfigurations("LocalShellTool round trip") { json ->
            val tool = OpenAIResponsesTool.LocalShellTool()

            json.decodeFromString<OpenAIResponsesTool.LocalShellTool>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test OpenAIResponsesTool_CustomTool round trip serialization`() =
        runWithBothJsonConfigurations("CustomTool round trip") { json ->
            val tool = OpenAIResponsesTool.CustomTool("custom1")

            json.decodeFromString<OpenAIResponsesTool.CustomTool>(
                json.encodeToString(tool)
            ).shouldNotBeNull()
        }

    @Test
    fun `test tool choice serializer invalid type error`() =
        runWithBothJsonConfigurations("tool choice invalid type") { json ->
            val invalidJson = buildJsonObject {
                put("type", JsonPrimitive("invalid_type"))
            }

            try {
                json.decodeFromJsonElement(OpenAIResponsesToolChoiceSerializer, invalidJson)
            } catch (e: Exception) {
                e.message shouldBe "Not recognize tool choice type: invalid_type"
            }
        }

    @Test
    fun `test tool choice serializer invalid element type error`() =
        runWithBothJsonConfigurations("tool choice invalid element") { json ->
            try {
                json.decodeFromJsonElement(OpenAIResponsesToolChoiceSerializer, buildJsonArray {})
            } catch (e: Exception) {
                e.message shouldBe "Tool choice must be either a string or an object"
            }
        }
}
