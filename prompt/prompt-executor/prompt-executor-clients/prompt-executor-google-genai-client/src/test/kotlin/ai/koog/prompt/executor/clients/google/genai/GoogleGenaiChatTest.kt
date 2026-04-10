package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.prompt.executor.clients.google.structure.GoogleBasicJsonSchemaGenerator
import ai.koog.prompt.executor.clients.google.structure.GoogleStandardJsonSchemaGenerator
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.ContentPart
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponseUsageMetadata
import com.google.genai.types.Part
import com.google.genai.types.Schema
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Base64

/**
 * Black-box tests for [GoogleGenaiLLMClient].
 *
 * Every test drives the client through the public [ai.koog.prompt.executor.clients.LLMClientAPI]
 * surface (`execute`, `executeMultipleChoices`, etc.) and verifies behaviour by:
 *   - capturing what was sent to `client.async.models.generateContent` (via mockk), or
 *   - asserting on the [Message.Response] list returned by `execute`.
 */
class GoogleGenaiChatTest {

    private val delegate: com.google.genai.Client
    private val asyncModels: com.google.genai.AsyncModels
    private val subject: CustomizedGoogleGenaiLLMClient

    // Aliases for readability
    private val flashModel get() = TestModels.flash
    private val thinkingModel get() = TestModels.thinking
    private val proModel get() = TestModels.pro
    private val fullCapabilityModel get() = TestModels.fullCapability
    private val completionOnlyModel get() = TestModels.completionOnly
    private val noCapModel get() = TestModels.noCap
    private val multiChoiceModel get() = TestModels.multiChoice
    private val multiChoiceNoCompletionModel get() = TestModels.multiChoiceNoCompletion

    init {
        val (d, am) = mockGoogleGenaiClient()
        delegate = d
        asyncModels = am
        subject = CustomizedGoogleGenaiLLMClient(delegate, models = TestModels.all)
    }

    private fun mockGenerateContent(response: GenerateContentResponse) =
        asyncModels.stubGenerateContent(response)

    // region Scenario: simple chat round-trip

    @Test
    fun `simple chat - system + user prompt produces correct SDK contents and config`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.System("You are a helpful assistant", RequestMetaInfo.Empty),
                Message.User("What is 2+2?", RequestMetaInfo.Empty),
            ),
            id = "simple-chat",
            params = GoogleParams(temperature = 0.7, maxTokens = 256)
        )
        val captured = mockGenerateContent(textResponse("4"))

        subject.execute(prompt, flashModel)

        // Verify customization overrides were invoked through the execute path
        subject.contentsCustomized shouldBe true
        subject.configCustomized shouldBe true

        // Only user message in contents
        captured.contents shouldHaveSize 1
        captured.contents[0].role().get() shouldBe "user"
        captured.contents[0].parts().get()[0].text().get() shouldBe "What is 2+2?"

        with(captured.config) {
            // Custom label added by CustomizedGoogleGenaiLLMClient.buildConfig

            labels().get() shouldBe mapOf("source" to "test")

            // System message extracted as systemInstruction in config
            val si = systemInstruction().get()
            si.shouldNotBeNull()
            si.parts().get()[0].text().get() shouldBe "You are a helpful assistant"

            // Config reflects params
            temperature().get() shouldBe 0.7f
            maxOutputTokens().get() shouldBe 256
            automaticFunctionCalling().get().disable().get() shouldBe true
        }
    }

    @Test
    fun `simple chat - text response parsed into Assistant message with metadata`() = runTest {
        val usageMetadata = GenerateContentResponseUsageMetadata.builder()
            .promptTokenCount(12).candidatesTokenCount(1).totalTokenCount(13).build()
        mockGenerateContent(textResponse("4", usageMetadata = usageMetadata))

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("What is 2+2?", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        subject.responseCustomized shouldBe true
        subject.metaInfoCustomized shouldBe true
        subject.candidateCustomized shouldBe true

        results shouldHaveSize 1
        results[0].shouldBeInstanceOf<Message.Assistant> {
            it.content shouldBe "4"
            it.finishReason shouldBe "STOP"
            it.metaInfo.inputTokensCount shouldBe 12
            it.metaInfo.outputTokensCount shouldBe 1
            it.metaInfo.totalTokensCount shouldBe 13
        }
    }

    // endregion

    // region Scenario: multi-turn conversation

    @Test
    fun `multi-turn - system + user + assistant + user produces correct content sequence`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.System("You are a math tutor", RequestMetaInfo.Empty),
                Message.User("What is 2+2?", RequestMetaInfo.Empty),
                Message.Assistant("4", metaInfo = ResponseMetaInfo.Empty),
                Message.User("And 3+3?", RequestMetaInfo.Empty),
            ),
            id = "multi-turn"
        )
        val captured = mockGenerateContent(textResponse("6"))

        subject.execute(prompt, flashModel)

        captured.config.systemInstruction().get().parts().get()[0].text().get() shouldBe "You are a math tutor"

        captured.contents shouldHaveSize 3
        captured.contents[0].role().get() shouldBe "user"
        captured.contents[0].parts().get()[0].text().get() shouldBe "What is 2+2?"
        captured.contents[1].role().get() shouldBe "model"
        captured.contents[1].parts().get()[0].text().get() shouldBe "4"
        captured.contents[2].role().get() shouldBe "user"
        captured.contents[2].parts().get()[0].text().get() shouldBe "And 3+3?"
    }

    // endregion

    // region Scenario: tool calling flow

    @Test
    fun `tool calling - full prompt with reasoning + parallel calls + results produces correct grouping`() =
        runTest {
            val sigAbc = Base64.getEncoder().encodeToString("sig-abc".toByteArray())
            val prompt = Prompt(
                messages = listOf(
                    Message.System("You can use tools", RequestMetaInfo.Empty),
                    Message.User("What's the weather in Paris and London?", RequestMetaInfo.Empty),
                    Message.Reasoning(encrypted = sigAbc, content = "", metaInfo = ResponseMetaInfo.Empty),
                    Message.Tool.Call(
                        id = "c1",
                        tool = "get_weather",
                        content = """{"city":"Paris"}""",
                        metaInfo = ResponseMetaInfo.Empty
                    ),
                    Message.Tool.Call(
                        id = "c2",
                        tool = "get_weather",
                        content = """{"city":"London"}""",
                        metaInfo = ResponseMetaInfo.Empty
                    ),
                    Message.Tool.Result(
                        id = "c1",
                        tool = "get_weather",
                        content = "Sunny 25C",
                        metaInfo = RequestMetaInfo.Empty
                    ),
                    Message.Tool.Result(
                        id = "c2",
                        tool = "get_weather",
                        content = "Rainy 15C",
                        metaInfo = RequestMetaInfo.Empty
                    ),
                ),
                id = "tool-flow"
            )
            val captured = mockGenerateContent(textResponse("It's sunny in Paris and rainy in London"))

            subject.execute(prompt, thinkingModel)

            captured.config.systemInstruction().get().shouldNotBeNull()
            captured.contents shouldHaveSize 3

            captured.contents[0].role().get() shouldBe "user"
            captured.contents[0].parts().get()[0].text().get() shouldBe "What's the weather in Paris and London?"

            val callParts = captured.contents[1].parts().get()
            callParts shouldHaveSize 2
            callParts[0].functionCall().get().name().get() shouldBe "get_weather"
            callParts[0].thoughtSignature().get() shouldBe "sig-abc".toByteArray()
            callParts[1].functionCall().get().name().get() shouldBe "get_weather"
            // Per Google API spec: only the first call in a batch carries the signature
            callParts[1].thoughtSignature().isPresent shouldBe false

            val resultParts = captured.contents[2].parts().get()
            resultParts shouldHaveSize 2
            resultParts[0].functionResponse().get().name().get() shouldBe "get_weather"
            resultParts[1].functionResponse().get().name().get() shouldBe "get_weather"
        }

    @Test
    fun `tool calling - response with function calls filters out text and preserves signatures`() = runTest {
        val sigBytes = "resp-sig".toByteArray()
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(
                            Content.builder().role("model").parts(
                                Part.fromText("Let me check the weather"),
                                Part.builder()
                                    .functionCall(
                                        FunctionCall.builder().name("get_weather").args(mapOf("city" to "Paris"))
                                            .build()
                                    )
                                    .thoughtSignature(sigBytes)
                                    .build()
                            ).build()
                        )
                        .finishReason("STOP")
                        .build()
                )
            ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("weather?", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        results.none { it is Message.Assistant } shouldBe true
        val reasoning = results.filterIsInstance<Message.Reasoning>().single()
        reasoning.encrypted shouldBe Base64.getEncoder().encodeToString(sigBytes)
        reasoning.content shouldBe ""
        val toolCall = results.filterIsInstance<Message.Tool.Call>().single()
        toolCall.tool shouldBe "get_weather"
        toolCall.content shouldBe """{"city":"Paris"}"""
    }

    @Test
    fun `tool calling - non-thinking model does not add signature to calls`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User("query", RequestMetaInfo.Empty),
                Message.Tool.Call(
                    id = "1",
                    tool = "search",
                    content = """{"q":"test"}""",
                    metaInfo = ResponseMetaInfo.Empty
                ),
            ),
            id = "no-sig"
        )
        val captured = mockGenerateContent(textResponse("result"))

        subject.execute(prompt, flashModel)

        val callPart = captured.contents[1].parts().get()[0]
        callPart.functionCall().get().name().get() shouldBe "search"
        callPart.thoughtSignature().isPresent shouldBe false
    }

    // endregion

    // region Scenario: thinking model conversation

    @Test
    fun `thinking model - reasoning with content becomes thought part in request`() = runTest {
        val thoughtSig1 = Base64.getEncoder().encodeToString("thought-sig-1".toByteArray())
        val prompt = Prompt(
            messages = listOf(
                Message.User("Explain quantum computing", RequestMetaInfo.Empty),
                Message.Reasoning(
                    content = "Let me think about this...",
                    encrypted = thoughtSig1,
                    metaInfo = ResponseMetaInfo.Empty
                ),
                Message.Assistant("Quantum computing uses qubits...", metaInfo = ResponseMetaInfo.Empty),
                Message.User("Tell me more", RequestMetaInfo.Empty),
            ),
            id = "thinking"
        )
        val captured = mockGenerateContent(textResponse("More details..."))

        subject.execute(prompt, thinkingModel)

        captured.contents shouldHaveSize 4
        val thoughtPart = captured.contents[1].parts().get()[0]
        thoughtPart.text().get() shouldBe "Let me think about this..."
        thoughtPart.thought().get() shouldBe true
        thoughtPart.thoughtSignature().get() shouldBe "thought-sig-1".toByteArray()
        captured.contents[2].parts().get()[0].text().get() shouldBe "Quantum computing uses qubits..."
        captured.contents[3].parts().get()[0].text().get() shouldBe "Tell me more"
    }

    @Test
    fun `thinking model - response with thought + text produces Reasoning + Assistant`() = runTest {
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(
                            Content.builder().role("model").parts(
                                Part.builder().text("I need to consider...").thought(true)
                                    .thoughtSignature("sig-x".toByteArray()).build(),
                                Part.fromText("The answer is 42.")
                            ).build()
                        )
                        .finishReason("STOP")
                        .build()
                )
            )
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder()
                    .promptTokenCount(20).candidatesTokenCount(30).totalTokenCount(50).thoughtsTokenCount(15)
                    .build()
            ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("question", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        results shouldHaveSize 2
        val reasoning = results[0].shouldBeInstanceOf<Message.Reasoning>()
        reasoning.content shouldBe "I need to consider..."
        reasoning.encrypted shouldBe Base64.getEncoder().encodeToString("sig-x".toByteArray())
        val assistant = results[1].shouldBeInstanceOf<Message.Assistant>()
        assistant.content shouldBe "The answer is 42."
        assistant.metaInfo.inputTokensCount shouldBe 20
        assistant.metaInfo.outputTokensCount shouldBe 30
    }

    // endregion

    // region Scenario: structured output

    @Test
    fun `structured output - Basic JSON schema sets responseMimeType and responseSchema in config`() = runTest {
        val prompt = Prompt(
            messages = listOf(Message.User("Give me a recipe", RequestMetaInfo.Empty)),
            id = "structured",
            params = GoogleParams(
                schema = LLMParams.Schema.JSON.Basic(
                    "recipe",
                    JsonObject(mapOf("type" to JsonPrimitive("object")))
                )
            )
        )
        val captured = mockGenerateContent(textResponse("""{"name":"pasta"}"""))

        subject.execute(prompt, proModel)

        captured.config.responseMimeType().get() shouldBe "application/json"
        captured.config.responseSchema().get() shouldBe Schema.fromJson("""{"type":"object"}""")
        captured.config.responseJsonSchema().isPresent shouldBe false
    }

    @Test
    fun `structured output - Standard JSON schema sets responseJsonSchema in config`() = runTest {
        val prompt = Prompt(
            messages = listOf(Message.User("Give me a recipe", RequestMetaInfo.Empty)),
            id = "structured",
            params = GoogleParams(
                schema = LLMParams.Schema.JSON.Standard(
                    "recipe",
                    JsonObject(mapOf("type" to JsonPrimitive("object")))
                )
            )
        )
        val captured = mockGenerateContent(textResponse("""{"name":"pasta"}"""))

        subject.execute(prompt, proModel)

        captured.config.responseMimeType().get() shouldBe "application/json"
        captured.config.responseJsonSchema().get() shouldBe mapOf("type" to "object")
        captured.config.responseSchema().isPresent shouldBe false
    }

    // endregion

    // region Scenario: multiple choices

    @Test
    fun `multiple choices - response with 3 candidates returns all with correct content`() = runTest {
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(Content.builder().role("model").parts(Part.fromText("Answer A")).build()).build(),
                    Candidate.builder()
                        .content(Content.builder().role("model").parts(Part.fromText("Answer B")).build()).build(),
                    Candidate.builder()
                        .content(Content.builder().role("model").parts(Part.fromText("Answer C")).build()).build(),
                )
            ).build()
        mockGenerateContent(response)

        val choices = subject.executeMultipleChoices(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            multiChoiceModel
        )

        choices.map { (it[0] as Message.Assistant).content } shouldBe listOf("Answer A", "Answer B", "Answer C")
    }

    // endregion

    // region Edge cases

    @Test
    fun `empty candidates throws LLMClientException`() = runTest {
        mockGenerateContent(GenerateContentResponse.builder().candidates(emptyList()).build())

        val error = assertThrows<LLMClientException> {
            subject.execute(
                Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
                flashModel
            )
        }
        error.message shouldContain "Empty candidates"
    }

    @Test
    fun `null content in candidate produces empty Assistant`() = runTest {
        mockGenerateContent(
            GenerateContentResponse.builder()
                .candidates(listOf(Candidate.builder().finishReason("STOP").build()))
                .build()
        )

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        results shouldHaveSize 1
        (results[0] as Message.Assistant).content shouldBe ""
    }

    @Test
    fun `null usageMetadata produces null token counts`() = runTest {
        mockGenerateContent(textResponse("x"))

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        val meta = results[0].metaInfo
        meta.totalTokensCount.shouldBeNull()
        meta.inputTokensCount.shouldBeNull()
        meta.outputTokensCount.shouldBeNull()
    }

    @Test
    fun `inline data image in response produces ContentPart Image`() = runTest {
        val response = GenerateContentResponse.builder().candidates(
            listOf(
                Candidate.builder().content(
                    Content.builder().role("model").parts(
                        listOf(
                            Part.builder().inlineData(
                                com.google.genai.types.Blob.builder().data("img".toByteArray())
                                    .mimeType("image/png")
                                    .build()
                            ).build()
                        )
                    ).build()
                ).build()
            )
        ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        val img = (results[0] as Message.Assistant).parts[0].shouldBeInstanceOf<ContentPart.Image>()
        img.format shouldBe "png"
        img.mimeType shouldBe "image/png"
    }

    @Test
    fun `inline data audio produces ContentPart Audio`() = runTest {
        val response = GenerateContentResponse.builder().candidates(
            listOf(
                Candidate.builder().content(
                    Content.builder().role("model").parts(
                        listOf(
                            Part.builder().inlineData(
                                com.google.genai.types.Blob.builder().data("audio".toByteArray())
                                    .mimeType("audio/mpeg")
                                    .build()
                            ).build()
                        )
                    ).build()
                ).build()
            )
        ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        val audio = (results[0] as Message.Assistant).parts[0].shouldBeInstanceOf<ContentPart.Audio>()
        audio.format shouldBe "mpeg"
        audio.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `inline data video produces ContentPart Video`() = runTest {
        val response = GenerateContentResponse.builder().candidates(
            listOf(
                Candidate.builder().content(
                    Content.builder().role("model").parts(
                        listOf(
                            Part.builder().inlineData(
                                com.google.genai.types.Blob.builder().data("video".toByteArray())
                                    .mimeType("video/mp4")
                                    .build()
                            ).build()
                        )
                    ).build()
                ).build()
            )
        ).build()
        mockGenerateContent(response)

        val results = subject.execute(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            flashModel
        )

        val video = (results[0] as Message.Assistant).parts[0].shouldBeInstanceOf<ContentPart.Video>()
        video.format shouldBe "mp4"
        video.mimeType shouldBe "video/mp4"
    }

    // endregion

    // region Config edge cases

    @Test
    fun `config passes system instruction`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.System("Be helpful", RequestMetaInfo.Empty),
                Message.User("hi", RequestMetaInfo.Empty)
            ),
            id = "t"
        )
        val captured = mockGenerateContent(textResponse("hello"))

        subject.execute(prompt, proModel)

        captured.config.systemInstruction().get().parts().get()[0].text().get() shouldBe "Be helpful"
    }

    @Test
    fun `config with default LLMParams has null optional fields`() = runTest {
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            proModel
        )

        captured.config.maxOutputTokens().isPresent shouldBe false
        captured.config.temperature().isPresent shouldBe false
        captured.config.automaticFunctionCalling().get().disable().get() shouldBe true
    }

    @Test
    fun `config omits temperature for model without Temperature capability`() = runTest {
        val noTempModel = completionOnlyModel // has Completion but no Temperature
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(
            Prompt(
                messages = listOf(Message.User("hi", RequestMetaInfo.Empty)),
                id = "t",
                params = GoogleParams(temperature = 0.5)
            ),
            noTempModel
        )

        captured.config.temperature().isPresent shouldBe false
    }

    @Test
    fun `config sets thinkingConfig with budget and includeThoughts`() = runTest {
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(
            Prompt(
                messages = listOf(Message.User("hi", RequestMetaInfo.Empty)),
                id = "t",
                params = GoogleParams(
                    thinkingConfig = GoogleThinkingConfig(
                        includeThoughts = true,
                        thinkingBudget = 99
                    )
                )
            ),
            thinkingModel
        )

        val tc = captured.config.thinkingConfig().get()
        tc.shouldNotBeNull()
        tc.includeThoughts().get() shouldBe true
        tc.thinkingBudget().get() shouldBe 99
    }

    // endregion

    // region Capability validation

    @Test
    fun `execute rejects model with mismatched provider`() = runTest {
        val model = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-3",
            capabilities = listOf(LLMCapability.Completion)
        )
        val error = assertThrows<IllegalArgumentException> {
            subject.execute(prompt = Prompt(messages = emptyList(), id = "t"), model = model)
        }
        error.message shouldContain "provider mismatch"
    }

    @Test
    fun `execute rejects model without Completion capability`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            subject.execute(prompt = Prompt(messages = emptyList(), id = "t"), model = noCapModel)
        }
        error.message shouldBe "Model test-no-cap does not support completion capability."
    }

    @Test
    fun `execute rejects tools when model lacks Tools capability and toolChoice is Required`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            subject.execute(
                prompt = Prompt(
                    messages = emptyList(),
                    id = "t",
                    params = GoogleParams(toolChoice = LLMParams.ToolChoice.Required)
                ),
                model = completionOnlyModel,
                tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = emptyList()))
            )
        }
        error.message shouldContain "does not support tools"
    }

    @Test
    fun `executeMultipleChoices rejects model with mismatched provider`() = runTest {
        val model = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.MultipleChoices)
        )
        val error = assertThrows<IllegalArgumentException> {
            subject.executeMultipleChoices(prompt = Prompt(messages = emptyList(), id = "t"), model = model)
        }
        error.message shouldContain "provider mismatch"
    }

    @Test
    fun `executeMultipleChoices rejects model without Completion capability`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            subject.executeMultipleChoices(
                prompt = Prompt(messages = emptyList(), id = "t"),
                model = multiChoiceNoCompletionModel
            )
        }
        error.message shouldContain "does not support completion capability"
    }

    @Test
    fun `executeMultipleChoices rejects model without MultipleChoices capability`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            subject.executeMultipleChoices(
                prompt = Prompt(messages = emptyList(), id = "t"),
                model = completionOnlyModel
            )
        }
        error.message shouldContain "does not support multipleChoices capability"
    }

    @Test
    fun `executeStreaming rejects model with mismatched provider`() = runTest {
        val model =
            LLModel(provider = LLMProvider.Anthropic, id = "claude", capabilities = listOf(LLMCapability.Completion))
        val error = assertThrows<IllegalArgumentException> {
            subject.executeStreaming(prompt = Prompt(messages = emptyList(), id = "t"), model = model).collect {}
        }
        error.message shouldContain "provider mismatch"
    }

    @Test
    fun `executeStreaming rejects model without Completion capability`() = runTest {
        val error = assertThrows<IllegalArgumentException> {
            subject.executeStreaming(prompt = Prompt(messages = emptyList(), id = "t"), model = noCapModel).collect {}
        }
        error.message shouldContain "does not support completion capability"
    }

    @Test
    fun `execute silently drops tools when model lacks Tools capability and toolChoice is optional`() = runTest {
        val tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = emptyList()))
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(
            prompt = Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            model = completionOnlyModel,
            tools = tools
        )

        captured.config.tools().isPresent shouldBe false
    }

    // endregion

    // region Multimodal user content

    @Test
    fun `user message with image is sent as inline data`() = runTest {
        val imageBytes = "fake-png".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.Text("Describe this image"),
                        ContentPart.Image(
                            content = AttachmentContent.Binary.Bytes(imageBytes),
                            format = "png",
                            mimeType = "image/png"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "multimodal"
        )
        val captured = mockGenerateContent(textResponse("A cat"))

        subject.execute(prompt, fullCapabilityModel)

        captured.contents shouldHaveSize 1
        val parts = captured.contents[0].parts().get()
        parts shouldHaveSize 2
        parts[0].text().get() shouldBe "Describe this image"
        val blob = parts[1].inlineData().get()
        blob.shouldNotBeNull()
        blob.mimeType().get() shouldBe "image/png"
        blob.data().get() shouldBe imageBytes
    }

    @Test
    fun `user message with audio is sent as inline data`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.Audio(
                            content = AttachmentContent.Binary.Bytes("fake-audio".toByteArray()),
                            format = "mp3",
                            mimeType = "audio/mpeg"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "audio"
        )
        val captured = mockGenerateContent(textResponse("transcript"))

        subject.execute(prompt, fullCapabilityModel)

        captured.contents[0].parts().get()[0].inlineData().get().mimeType().get() shouldBe "audio/mpeg"
    }

    @Test
    fun `user message with video is sent as inline data`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.Video(
                            content = AttachmentContent.Binary.Bytes("fake-video".toByteArray()),
                            format = "mp4",
                            mimeType = "video/mp4"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "video"
        )
        val captured = mockGenerateContent(textResponse("video description"))

        subject.execute(prompt, fullCapabilityModel)

        captured.contents[0].parts().get()[0].inlineData().get().mimeType().get() shouldBe "video/mp4"
    }

    @Test
    fun `user message with file is sent as inline data`() = runTest {
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.File(
                            content = AttachmentContent.Binary.Bytes("fake-pdf".toByteArray()),
                            format = "pdf",
                            mimeType = "application/pdf"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "file"
        )
        val captured = mockGenerateContent(textResponse("document summary"))

        subject.execute(prompt, fullCapabilityModel)

        captured.contents[0].parts().get()[0].inlineData().get().mimeType().get() shouldBe "application/pdf"
    }

    // endregion

    // region ThinkingLevel

    @ParameterizedTest
    @CsvSource("LOW, LOW", "HIGH, HIGH")
    fun `config sets thinkingLevel`(thinkingLevel: GoogleThinkingLevel, expectedLevel: String) = runTest {
        val captured = mockGenerateContent(textResponse("ok"))

        subject.execute(
            Prompt(
                messages = listOf(Message.User("hi", RequestMetaInfo.Empty)),
                id = "t",
                params = GoogleParams(thinkingConfig = GoogleThinkingConfig(thinkingLevel = thinkingLevel))
            ),
            thinkingModel
        )

        captured.config.thinkingConfig().get().thinkingLevel().get().toString() shouldBe expectedLevel
    }

    // endregion

    // region Schema generators

    @Test
    fun `getBasicJsonSchemaGenerator returns GoogleBasicJsonSchemaGenerator`() {
        subject.getBasicJsonSchemaGenerator().shouldBeInstanceOf<GoogleBasicJsonSchemaGenerator>()
    }

    @Test
    fun `getStandardJsonSchemaGenerator returns GoogleStandardJsonSchemaGenerator`() {
        subject.getStandardJsonSchemaGenerator().shouldBeInstanceOf<GoogleStandardJsonSchemaGenerator>()
    }

    // endregion
}
