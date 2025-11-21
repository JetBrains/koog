package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.test.utils.runWithBothJsonConfigurations
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class OpenAIResponsesAPIResponseTest {

    @Test
    fun `test OpenAIResponsesAPIResponse minimal serialization`() =
        runWithBothJsonConfigurations("minimal response serialization") { json ->
            val response = OpenAIResponsesAPIResponse(
                created = 1699500000L,
                id = "response_123",
                model = "gpt-4o",
                output = listOf(
                    Item.OutputMessage(
                        content = listOf(
                            OutputContent.Text(annotations = emptyList(), text = "Hello, world!")
                        ),
                        id = "msg_minimal_123"
                    )
                ),
                parallelToolCalls = false,
                status = OpenAIInputStatus.COMPLETED,
                text = OpenAITextConfig()
            )

            json.decodeFromString<OpenAIResponsesAPIResponse>(json.encodeToString(response)).shouldNotBeNull {
                created shouldBe 1699500000L
                id shouldBe "response_123"
                model shouldBe "gpt-4o"
                output shouldHaveSize 1
                parallelToolCalls shouldBe false
                status shouldBe OpenAIInputStatus.COMPLETED
            }
        }

    @Test
    fun `test OpenAIResponsesAPIResponse full serialization`() =
        runWithBothJsonConfigurations("full response serialization") { json ->
            val response = OpenAIResponsesAPIResponse(
                background = true,
                created = 1699500000L,
                error = OpenAIResponsesAPIResponse.ResponseError("rate_limit", "Rate limit exceeded"),
                id = "response_456",
                incompleteDetails = OpenAIResponsesAPIResponse.IncompleteDetails("token_limit"),
                instructions = listOf(Item.Text("System instruction")),
                maxOutputTokens = 1000,
                maxToolCalls = 5,
                metadata = mapOf("session" to "abc123", "user" to "user456"),
                model = "gpt-4o-mini",
                output = listOf(
                    Item.OutputMessage(
                        content = listOf(
                            OutputContent.Text(annotations = emptyList(), text = "Response text")
                        ),
                        id = "msg_123"
                    )
                ),
                outputText = "Response text",
                parallelToolCalls = true,
                previousResponseId = "prev_response_123",
                prompt = OpenAIPromptReference(
                    id = "prompt_123",
                    variables = mapOf("name" to "John"),
                    version = "1.0"
                ),
                promptCacheKey = "cache_key_456",
                reasoning = ReasoningConfig(
                    effort = ReasoningEffort.HIGH,
                    summary = ReasoningSummary.DETAILED
                ),
                safetyIdentifier = "safety_789",
                serviceTier = ServiceTier.FLEX,
                status = OpenAIInputStatus.COMPLETED,
                temperature = 0.7,
                text = OpenAITextConfig(
                    format = OpenAIOutputFormat.JsonSchema(
                        name = "response_schema",
                        schema = buildJsonObject {
                            put("type", JsonPrimitive("object"))
                            put(
                                "properties",
                                buildJsonObject {
                                    put(
                                        "message",
                                        buildJsonObject {
                                            put("type", JsonPrimitive("string"))
                                        }
                                    )
                                }
                            )
                        }
                    ),
                    verbosity = TextVerbosity.HIGH
                ),
                toolChoice = OpenAIResponsesToolChoice.Mode("auto"),
                tools = listOf(
                    OpenAIResponsesTool.Function(
                        name = "test_function",
                        parameters = buildJsonObject {
                            put("type", JsonPrimitive("object"))
                        }
                    )
                ),
                topLogprobs = 3,
                topP = 0.9,
                truncation = "auto",
                usage = OpenAIResponsesAPIResponse.Usage(
                    inputTokens = 100,
                    inputTokensDetails = OpenAIResponsesAPIResponse.Usage.InputTokensDetails(10),
                    outputTokens = 200,
                    outputTokensDetails = OpenAIResponsesAPIResponse.Usage.OutputTokensDetails(50),
                    totalTokens = 300
                ),
                user = "deprecated_user"
            )

            json.decodeFromString<OpenAIResponsesAPIResponse>(json.encodeToString(response)).shouldNotBeNull {
                background shouldBe true
                created shouldBe 1699500000L
                error.shouldNotBeNull {
                    code shouldBe "rate_limit"
                    message shouldBe "Rate limit exceeded"
                }
                id shouldBe "response_456"
                incompleteDetails.shouldNotBeNull {
                    reason shouldBe "token_limit"
                }
                instructions.shouldNotBeNull { shouldHaveSize(1) }
                maxOutputTokens shouldBe 1000
                maxToolCalls shouldBe 5
                metadata shouldBe mapOf("session" to "abc123", "user" to "user456")
                model shouldBe "gpt-4o-mini"
                output.shouldNotBeNull { shouldHaveSize(1) }
                outputText shouldBe "Response text"
                parallelToolCalls shouldBe true
                previousResponseId shouldBe "prev_response_123"
                prompt.shouldNotBeNull {
                    id shouldBe "prompt_123"
                    variables shouldBe mapOf("name" to "John")
                    version shouldBe "1.0"
                }
                promptCacheKey shouldBe "cache_key_456"
                reasoning.shouldNotBeNull {
                    effort shouldBe ReasoningEffort.HIGH
                    summary shouldBe ReasoningSummary.DETAILED
                }
                safetyIdentifier shouldBe "safety_789"
                serviceTier shouldBe ServiceTier.FLEX
                status shouldBe OpenAIInputStatus.COMPLETED
                temperature shouldBe 0.7
                text.shouldNotBeNull {
                    format.shouldNotBeNull()
                    verbosity shouldBe TextVerbosity.HIGH
                }
                toolChoice.shouldNotBeNull()
                tools.shouldNotBeNull { shouldHaveSize(1) }
                topLogprobs shouldBe 3
                topP shouldBe 0.9
                truncation shouldBe "auto"
                usage.shouldNotBeNull {
                    inputTokens shouldBe 100
                    outputTokens shouldBe 200
                    totalTokens shouldBe 300
                }
                user shouldBe "deprecated_user"
            }
        }

    @Test
    fun `test OpenAIResponsesAPIResponse deserialization from JSON`() =
        runWithBothJsonConfigurations("response deserialization") { json ->
            val jsonInput = buildJsonObject {
                put("created_at", JsonPrimitive(1699500000L))
                put("id", JsonPrimitive("response_789"))
                put("model", JsonPrimitive("gpt-4"))
                put("object", JsonPrimitive("response"))
                put(
                    "output",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("type", JsonPrimitive("message"))
                                put("id", JsonPrimitive("msg_test_123"))
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", JsonPrimitive("output_text"))
                                                put("annotations", buildJsonArray { })
                                                put("text", JsonPrimitive("Test response"))
                                            }
                                        )
                                    }
                                )
                                put("role", JsonPrimitive("assistant"))
                            }
                        )
                    }
                )
                put("parallelToolCalls", JsonPrimitive(true))
                put("status", JsonPrimitive("completed"))
                put("text", buildJsonObject { })
            }

            json.decodeFromJsonElement<OpenAIResponsesAPIResponse>(jsonInput).shouldNotBeNull {
                created shouldBe 1699500000L
                id shouldBe "response_789"
                model shouldBe "gpt-4"
                objectType shouldBe "response"
                output shouldHaveSize 1
                parallelToolCalls shouldBe true
                status shouldBe OpenAIInputStatus.COMPLETED
            }
        }

    @Test
    fun `test ResponseError serialization`() = runWithBothJsonConfigurations("ResponseError serialization") { json ->
        val jsonString = json.encodeToString(
            OpenAIResponsesAPIResponse.ResponseError(
                code = "invalid_request",
                message = "The request was invalid"
            )
        )

        jsonString shouldEqualJson """
            {
                "code": "invalid_request",
                "message": "The request was invalid"
            }
        """.trimIndent()

        json.decodeFromString<OpenAIResponsesAPIResponse.ResponseError>(jsonString).shouldNotBeNull {
            code shouldBe "invalid_request"
            message shouldBe "The request was invalid"
        }
    }

    @Test
    fun `test IncompleteDetails serialization`() =
        runWithBothJsonConfigurations("IncompleteDetails serialization") { json ->
            val jsonString = json.encodeToString(OpenAIResponsesAPIResponse.IncompleteDetails("max_tokens"))

            jsonString shouldEqualJson """
            {
                "reason": "max_tokens"
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesAPIResponse.IncompleteDetails>(jsonString).reason shouldBe "max_tokens"
        }

    @Test
    fun `test Usage serialization`() = runWithBothJsonConfigurations("Usage serialization") { json ->
        val jsonString = json.encodeToString(
            OpenAIResponsesAPIResponse.Usage(
                inputTokens = 150,
                inputTokensDetails = OpenAIResponsesAPIResponse.Usage.InputTokensDetails(25),
                outputTokens = 300,
                outputTokensDetails = OpenAIResponsesAPIResponse.Usage.OutputTokensDetails(100),
                totalTokens = 450
            )
        )

        jsonString shouldEqualJson """
            {
                "inputTokens": 150,
                "inputTokensDetails": {"cachedTokens": 25},
                "outputTokens": 300,
                "outputTokensDetails": {"reasoningTokens": 100},
                "totalTokens": 450
            }
        """.trimIndent()

        json.decodeFromString<OpenAIResponsesAPIResponse.Usage>(jsonString).shouldNotBeNull {
            inputTokens shouldBe 150
            inputTokensDetails.cachedTokens shouldBe 25
            outputTokens shouldBe 300
            outputTokensDetails.reasoningTokens shouldBe 100
            totalTokens shouldBe 450
        }
    }

    @Test
    fun `test OpenAIPromptReference serialization`() =
        runWithBothJsonConfigurations("OpenAIPromptReference serialization") { json ->
            val jsonString = json.encodeToString(
                OpenAIPromptReference(
                    id = "prompt_ref_123",
                    variables = mapOf(
                        "name" to "Alice",
                        "age" to "30",
                        "city" to "New York"
                    ),
                    version = "2.1"
                )
            )

            jsonString shouldEqualJson """
            {
                "id": "prompt_ref_123",
                "variables": {
                    "name": "Alice",
                    "age": "30",
                    "city": "New York"
                },
                "version": "2.1"
            }
            """.trimIndent()

            json.decodeFromString<OpenAIPromptReference>(jsonString).shouldNotBeNull {
                id shouldBe "prompt_ref_123"
                variables shouldBe mapOf(
                    "name" to "Alice",
                    "age" to "30",
                    "city" to "New York"
                )
                version shouldBe "2.1"
            }
        }

    @Test
    fun `test ReasoningConfig serialization`() =
        runWithBothJsonConfigurations("ReasoningConfig serialization") { json ->
            val jsonString = json.encodeToString(
                ReasoningConfig(
                    effort = ReasoningEffort.MEDIUM,
                    summary = ReasoningSummary.CONCISE
                )
            )

            jsonString shouldEqualJson """
            {
                "effort": "medium",
                "summary": "concise"
            }
            """.trimIndent()

            json.decodeFromString<ReasoningConfig>(jsonString).shouldNotBeNull {
                effort shouldBe ReasoningEffort.MEDIUM
                summary shouldBe ReasoningSummary.CONCISE
            }
        }

    @Test
    fun `test OpenAITextConfig serialization with JsonSchema format`() =
        runWithBothJsonConfigurations("OpenAITextConfig with JsonSchema") { json ->
            val textConfig = OpenAITextConfig(
                format = OpenAIOutputFormat.JsonSchema(
                    name = "user_profile",
                    schema = buildJsonObject {
                        put("type", JsonPrimitive("object"))
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "name",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("string"))
                                    }
                                )
                                put(
                                    "age",
                                    buildJsonObject {
                                        put("type", JsonPrimitive("integer"))
                                    }
                                )
                            }
                        )
                        put(
                            "required",
                            buildJsonArray {
                                add(JsonPrimitive("name"))
                            }
                        )
                    },
                    description = "User profile schema",
                    strict = true
                ),
                verbosity = TextVerbosity.MEDIUM
            )

            json.decodeFromString<OpenAITextConfig>(json.encodeToString(textConfig)).shouldNotBeNull {
                (format as OpenAIOutputFormat.JsonSchema).shouldNotBeNull {
                    name shouldBe "user_profile"
                    description shouldBe "User profile schema"
                    strict shouldBe true
                    schema["type"]?.jsonPrimitive?.content shouldBe "object"
                }
                verbosity shouldBe TextVerbosity.MEDIUM
            }
        }

    @Test
    fun `test OpenAITextConfig with all output formats`() =
        runWithBothJsonConfigurations("OpenAITextConfig all formats") { json ->
            listOf(
                OpenAITextConfig(format = OpenAIOutputFormat.Text()),
                OpenAITextConfig(format = OpenAIOutputFormat.JsonObject()),
                OpenAITextConfig(
                    format = OpenAIOutputFormat.JsonSchema(
                        name = "test_schema",
                        schema = buildJsonObject { put("type", JsonPrimitive("object")) }
                    )
                )
            ).forEach { config ->
                val jsonString = json.encodeToString(config)
                val decoded = json.decodeFromString<OpenAITextConfig>(jsonString)
                decoded.format.shouldNotBeNull()

                when (config.format) {
                    is OpenAIOutputFormat.Text -> decoded.format.shouldBeTypeOf<OpenAIOutputFormat.Text>()
                    is OpenAIOutputFormat.JsonObject -> decoded.format.shouldBeTypeOf<OpenAIOutputFormat.JsonObject>()
                    is OpenAIOutputFormat.JsonSchema -> {
                        val decodedSchema = decoded.format as OpenAIOutputFormat.JsonSchema
                        decodedSchema.name shouldBe "test_schema"
                    }

                    null -> {
                        throw AssertionError("Unexpected format: ${config.format}")
                    }
                }
            }
        }

    @Test
    fun `test OpenAIResponsesAPIStreamOptions serialization`() =
        runWithBothJsonConfigurations("OpenAIResponsesAPIStreamOptions") { json ->
            val jsonString = json.encodeToString(OpenAIResponsesAPIStreamOptions(includeObfuscation = false))

            jsonString shouldEqualJson """
            {
                "includeObfuscation": false
            }
            """.trimIndent()

            json.decodeFromString<OpenAIResponsesAPIStreamOptions>(jsonString).includeObfuscation shouldBe false
        }

    @Test
    fun `test McpTool serialization`() = runWithBothJsonConfigurations("McpTool serialization") { json ->
        val mcpTool = McpTool(
            inputSchema = buildJsonObject {
                put("type", JsonPrimitive("object"))
                put(
                    "properties",
                    buildJsonObject {
                        put(
                            "query",
                            buildJsonObject {
                                put("type", JsonPrimitive("string"))
                            }
                        )
                    }
                )
            },
            name = "search_tool",
            annotations = buildJsonObject {
                put("description", JsonPrimitive("A search tool"))
            },
            description = "Tool for searching content"
        )

        json.decodeFromString<McpTool>(json.encodeToString(mcpTool)).shouldNotBeNull {
            name shouldBe "search_tool"
            description shouldBe "Tool for searching content"
            inputSchema["type"]?.jsonPrimitive?.content shouldBe "object"
            annotations?.get("description")?.jsonPrimitive?.content shouldBe "A search tool"
        }
    }
}
