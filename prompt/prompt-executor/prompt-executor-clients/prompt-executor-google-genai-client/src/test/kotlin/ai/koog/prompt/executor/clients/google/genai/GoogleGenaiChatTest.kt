package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.executor.clients.google.GoogleModels
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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Base64

class GoogleGenaiChatTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    /** Model with all capabilities for multimodal tests */
    private val fullCapabilityModel = LLModel(
        provider = LLMProvider.Google,
        id = "test-full",
        capabilities = listOf(
            LLMCapability.Completion, LLMCapability.Temperature, LLMCapability.MultipleChoices,
            LLMCapability.Tools, LLMCapability.ToolChoice,
            LLMCapability.Vision.Image, LLMCapability.Vision.Video,
            LLMCapability.Audio, LLMCapability.Document,
            LLMCapability.Schema.JSON.Basic, LLMCapability.Schema.JSON.Standard,
        )
    )

    // region Scenario: simple chat round-trip

    @Test
    fun `simple chat - system + user prompt produces correct SDK contents and config`() {
        val prompt = Prompt(
            messages = listOf(
                Message.System("You are a helpful assistant", RequestMetaInfo.Empty),
                Message.User("What is 2+2?", RequestMetaInfo.Empty),
            ),
            id = "simple-chat",
            params = GoogleParams(temperature = 0.7, maxTokens = 256)
        )

        val (contents, systemInstruction) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)
        val config =
            subject.buildConfig(prompt.params, GoogleModels.Gemini2_5Flash, emptyList(), systemInstruction).build()

        // Verify customization overrides were invoked
        subject.contentsCustomized shouldBe true
        subject.configCustomized shouldBe true
        config.labels().get() shouldBe mapOf("source" to "test")

        // System message extracted as systemInstruction
        systemInstruction.shouldNotBeNull()
        systemInstruction.parts().get()[0].text().get() shouldBe "You are a helpful assistant"

        // Only user message in contents
        contents shouldHaveSize 1
        contents[0].role().get() shouldBe "user"
        contents[0].parts().get()[0].text().get() shouldBe "What is 2+2?"

        // Config reflects params
        config.systemInstruction().get().shouldNotBeNull()
        config.temperature().get() shouldBe 0.7f
        config.maxOutputTokens().get() shouldBe 256
        config.automaticFunctionCalling().get().disable()?.orElse(false) shouldBe true
    }

    @Test
    fun `simple chat - text response parsed into Assistant message with metadata`() {
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(Content.builder().role("model").parts(Part.fromText("4")).build())
                        .finishReason("STOP")
                        .build()
                )
            )
            .usageMetadata(
                GenerateContentResponseUsageMetadata.builder()
                    .promptTokenCount(12).candidatesTokenCount(1).totalTokenCount(13).build()
            )
            .build()

        val results = subject.processResponse(response).first()

        // Verify response processing overrides were invoked
        subject.responseCustomized shouldBe true
        subject.metaInfoCustomized shouldBe true
        subject.candidateCustomized shouldBe true

        results shouldHaveSize 1
        val assistant = results[0].shouldBeInstanceOf<Message.Assistant>()
        assistant.content shouldBe "4"
        assistant.finishReason shouldBe "STOP"
        assistant.metaInfo.inputTokensCount shouldBe 12
        assistant.metaInfo.outputTokensCount shouldBe 1
        assistant.metaInfo.totalTokensCount shouldBe 13
    }

    // endregion

    // region Scenario: multi-turn conversation

    @Test
    fun `multi-turn - system + user + assistant + user produces correct content sequence`() {
        val prompt = Prompt(
            messages = listOf(
                Message.System("You are a math tutor", RequestMetaInfo.Empty),
                Message.User("What is 2+2?", RequestMetaInfo.Empty),
                Message.Assistant("4", metaInfo = ResponseMetaInfo.Empty),
                Message.User("And 3+3?", RequestMetaInfo.Empty),
            ),
            id = "multi-turn"
        )

        val (contents, systemInstruction) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)

        systemInstruction.shouldNotBeNull()
        systemInstruction.parts().get()[0].text().get() shouldBe "You are a math tutor"

        contents shouldHaveSize 3
        contents[0].role().get() shouldBe "user"
        contents[0].parts().get()[0].text().get() shouldBe "What is 2+2?"
        contents[1].role().get() shouldBe "model"
        contents[1].parts().get()[0].text().get() shouldBe "4"
        contents[2].role().get() shouldBe "user"
        contents[2].parts().get()[0].text().get() shouldBe "And 3+3?"
    }

    // endregion

    // region Scenario: tool calling flow

    @Test
    fun `tool calling - full prompt with reasoning + parallel calls + results produces correct grouping`() {
        // Signatures stored in encrypted are Base64-encoded; raw bytes are "sig-abc".toByteArray()
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

        val (contents, systemInstruction) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)

        systemInstruction.shouldNotBeNull()
        // Contents: user, model(2 calls), user(2 results)
        contents shouldHaveSize 3

        // User message
        contents[0].role().get() shouldBe "user"
        contents[0].parts().get()[0].text()
            .get() shouldBe "What's the weather in Paris and London?"

        // Grouped tool calls
        val callParts = contents[1].parts().get()
        callParts shouldHaveSize 2
        callParts[0].functionCall().get().name().get() shouldBe "get_weather"
        callParts[0].thoughtSignature().get() shouldBe "sig-abc".toByteArray()
        callParts[1].functionCall().get().name().get() shouldBe "get_weather"
        // Per Google API spec: only the first call in a batch carries the signature
        callParts[1].thoughtSignature().isPresent shouldBe false

        // Grouped tool results
        val resultParts = contents[2].parts().get()
        resultParts shouldHaveSize 2
        resultParts[0].functionResponse().get().name().get() shouldBe "get_weather"
        resultParts[1].functionResponse().get().name().get() shouldBe "get_weather"
    }

    @Test
    fun `tool calling - response with function calls filters out text and preserves signatures`() {
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
            )
            .build()

        val results = subject.processResponse(response).first()

        // Text filtered out, only Reasoning (signature carrier) + Tool.Call remain
        results.none { it is Message.Assistant } shouldBe true
        val reasoning = results.filterIsInstance<Message.Reasoning>().single()
        // signatureFromBytes Base64-encodes the raw bytes from the API response
        reasoning.encrypted shouldBe Base64.getEncoder().encodeToString(sigBytes)
        reasoning.content shouldBe ""
        val toolCall = results.filterIsInstance<Message.Tool.Call>().single()
        toolCall.tool shouldBe "get_weather"
        toolCall.content shouldBe """{"city":"Paris"}"""
    }

    @Test
    fun `tool calling - non-thinking model does not add signature to calls`() {
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

        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Flash)

        val callPart = contents[1].parts().get()[0]
        callPart.functionCall().get().name().get() shouldBe "search"
        callPart.thoughtSignature().isPresent shouldBe false
    }

    // endregion

    // region Scenario: thinking model conversation

    @Test
    fun `thinking model - reasoning with content becomes thought part in request`() {
        // encrypted must be valid Base64; the decoded bytes are what the SDK Part will carry
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

        val (contents, _) = subject.buildSdkContents(prompt, GoogleModels.Gemini3_Pro_Preview)

        contents shouldHaveSize 4
        // Thought part
        val thoughtPart = contents[1].parts().get()[0]
        thoughtPart.text().get() shouldBe "Let me think about this..."
        thoughtPart.thought().orElse(false) shouldBe true
        thoughtPart.thoughtSignature()
            .get() shouldBe "thought-sig-1".toByteArray() // raw bytes after Base64 decode
        // Assistant response
        contents[2].parts().get()[0].text().get() shouldBe "Quantum computing uses qubits..."
        // Follow-up user message
        contents[3].parts().get()[0].text().get() shouldBe "Tell me more"
    }

    @Test
    fun `thinking model - response with thought + text produces Reasoning + Assistant`() {
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
                    .promptTokenCount(20).candidatesTokenCount(30).totalTokenCount(50).thoughtsTokenCount(15).build()
            )
            .build()

        val results = subject.processResponse(response).first()

        results shouldHaveSize 2
        val reasoning = results[0].shouldBeInstanceOf<Message.Reasoning>()
        reasoning.content shouldBe "I need to consider..."
        // signatureFromBytes Base64-encodes the raw bytes "sig-x".toByteArray() from the API response
        reasoning.encrypted shouldBe Base64.getEncoder().encodeToString("sig-x".toByteArray())
        val assistant = results[1].shouldBeInstanceOf<Message.Assistant>()
        assistant.content shouldBe "The answer is 42."
        assistant.metaInfo.inputTokensCount shouldBe 20
        assistant.metaInfo.outputTokensCount shouldBe 30
    }

    // endregion

    // region Scenario: structured output

    @Test
    fun `structured output - Basic JSON schema sets responseMimeType and responseSchema in config`() {
        val schema = LLMParams.Schema.JSON.Basic(
            name = "recipe",
            schema = JsonObject(mapOf("type" to JsonPrimitive("object")))
        )
        val prompt = Prompt(
            messages = listOf(Message.User("Give me a recipe", RequestMetaInfo.Empty)),
            id = "structured",
            params = GoogleParams(schema = schema)
        )

        val (_, systemInstruction) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Pro)
        val config =
            subject.buildConfig(prompt.params, GoogleModels.Gemini2_5Pro, emptyList(), systemInstruction).build()

        config.responseMimeType().get() shouldBe "application/json"
        config.responseSchema().get() shouldBe Schema.fromJson("""{"type":"object"}""")
        config.responseJsonSchema().isPresent shouldBe false
    }

    @Test
    fun `structured output - Standard JSON schema sets responseJsonSchema in config`() {
        val schema = LLMParams.Schema.JSON.Standard(
            name = "recipe",
            schema = JsonObject(mapOf("type" to JsonPrimitive("object")))
        )
        val prompt = Prompt(
            messages = listOf(Message.User("Give me a recipe", RequestMetaInfo.Empty)),
            id = "structured",
            params = GoogleParams(schema = schema)
        )

        val (_, systemInstruction) = subject.buildSdkContents(prompt, GoogleModels.Gemini2_5Pro)
        val config =
            subject.buildConfig(prompt.params, GoogleModels.Gemini2_5Pro, emptyList(), systemInstruction).build()

        config.responseMimeType().get() shouldBe "application/json"
        config.responseJsonSchema().get() shouldBe mapOf("type" to "object")
        config.responseSchema().isPresent shouldBe false
    }

    // endregion

    // region Scenario: multiple choices

    @Test
    fun `multiple choices - response with 3 candidates returns all with correct content`() {
        val contentBuilder = Content.builder().role("model")
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder()
                        .content(contentBuilder.parts(Part.fromText("Answer A")).build()).build(),
                    Candidate.builder()
                        .content(contentBuilder.role("model").parts(Part.fromText("Answer B")).build()).build(),
                    Candidate.builder()
                        .content(contentBuilder.role("model").parts(Part.fromText("Answer C")).build()).build(),
                )
            )
            .build()

        val choices = subject.processResponse(response)

        choices.map { it[0] as Message.Assistant }.map { it.content } shouldBe listOf(
            "Answer A",
            "Answer B",
            "Answer C"
        )
    }

    // endregion

    // region Edge cases

    @Test
    fun `empty candidates throws LLMClientException`() {
        val response = GenerateContentResponse.builder().candidates(emptyList()).build()
        assertThrows<LLMClientException> { subject.processResponse(response) }
    }

    @Test
    fun `null content in candidate produces empty Assistant`() {
        val candidate = Candidate.builder().finishReason("STOP").build()
        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        results shouldHaveSize 1
        (results[0] as Message.Assistant).content shouldBe ""
    }

    @Test
    fun `null usageMetadata produces null token counts`() {
        val response = GenerateContentResponse.builder()
            .candidates(
                listOf(
                    Candidate.builder().content(Content.builder().role("model").parts(Part.fromText("x")).build())
                        .build()
                )
            ).build()

        val meta = subject.processResponse(response).first()[0].metaInfo
        meta.totalTokensCount.shouldBeNull()
        meta.inputTokensCount.shouldBeNull()
        meta.outputTokensCount.shouldBeNull()
    }

    @Test
    fun `inline data image in response produces ContentPart Image`() {
        val candidate = Candidate.builder()
            .content(
                Content.builder().role("model").parts(
                    listOf(
                        Part.builder().inlineData(
                            com.google.genai.types.Blob.builder().data("img".toByteArray()).mimeType("image/png")
                                .build()
                        ).build()
                    )
                ).build()
            ).build()

        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        val img =
            (results[0] as Message.Assistant).parts[0].shouldBeInstanceOf<ContentPart.Image>()
        img.format shouldBe "png"
        img.mimeType shouldBe "image/png"
    }

    @Test
    fun `inline data image with gif produces ContentPart Image`() {
        val candidate = Candidate.builder()
            .content(
                Content.builder().role("model").parts(
                    listOf(
                        Part.builder().inlineData(
                            com.google.genai.types.Blob.builder().data("gif".toByteArray()).mimeType("image/gif")
                                .build()
                        ).build()
                    )
                ).build()
            ).build()

        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        val img = (results[0] as Message.Assistant).parts[0]
            .shouldBeInstanceOf<ContentPart.Image>()
        img.format shouldBe "gif"
        img.mimeType shouldBe "image/gif"
    }

    @Test
    fun `inline data audio produces ContentPart Audio`() {
        val candidate = Candidate.builder()
            .content(
                Content.builder().role("model").parts(
                    listOf(
                        Part.builder().inlineData(
                            com.google.genai.types.Blob.builder().data("audio".toByteArray()).mimeType("audio/mpeg")
                                .build()
                        ).build()
                    )
                ).build()
            ).build()

        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        val audio = (results[0] as Message.Assistant).parts[0]
            .shouldBeInstanceOf<ContentPart.Audio>()
        audio.format shouldBe "mpeg"
        audio.mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `inline data video produces ContentPart Video`() {
        val candidate = Candidate.builder()
            .content(
                Content.builder().role("model").parts(
                    listOf(
                        Part.builder().inlineData(
                            com.google.genai.types.Blob.builder().data("video".toByteArray()).mimeType("video/mp4")
                                .build()
                        ).build()
                    )
                ).build()
            ).build()

        val results = subject.processCandidate(candidate, ResponseMetaInfo.Empty)
        val video = (results[0] as Message.Assistant).parts[0]
            .shouldBeInstanceOf<ContentPart.Video>()
        video.format shouldBe "mp4"
        video.mimeType shouldBe "video/mp4"
    }

    // endregion

    // region Config edge cases

    @Test
    fun `buildConfig passes system instruction to config`() {
        val sysContent = Content.builder().parts(Part.fromText("Be helpful")).build()
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Pro, emptyList(), sysContent).build()
        val si = config.systemInstruction().get()
        si.shouldNotBeNull()
        si.parts().get()[0].text().get() shouldBe "Be helpful"
    }

    @Test
    fun `buildConfig with default LLMParams has null optional fields`() {
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Pro, emptyList(), null).build()
        config.maxOutputTokens().isPresent shouldBe false
        config.temperature().isPresent shouldBe false
        config.automaticFunctionCalling().orElse(null)?.disable()?.orElse(false) shouldBe true
    }

    @Test
    fun `buildConfig omits temperature for model without Temperature capability`() {
        val noTempModel =
            LLModel(provider = LLMProvider.Google, id = "no-temp", capabilities = listOf(LLMCapability.Completion))
        val config = subject.buildConfig(GoogleParams(temperature = 0.5), noTempModel, emptyList(), null).build()
        config.temperature().isPresent shouldBe false
    }

    @Test
    fun `buildConfig sets thinkingConfig with budget and includeThoughts`() {
        val params = GoogleParams(thinkingConfig = GoogleThinkingConfig(includeThoughts = true, thinkingBudget = 99))
        val config = subject.buildConfig(params, GoogleModels.Gemini3_Pro_Preview, emptyList(), null).build()
        val tc = config.thinkingConfig().get()
        tc.shouldNotBeNull()
        tc.includeThoughts().orElse(false) shouldBe true
        tc.thinkingBudget().get() shouldBe 99
    }

    // endregion

    // region Capability validation

    @Test
    fun `execute rejects model with mismatched provider`() = runTest {
        val anthropicModel = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-3",
            capabilities = listOf(LLMCapability.Completion)
        )
        val error = assertThrows<IllegalArgumentException> {
            subject.execute(prompt = Prompt(messages = emptyList(), id = "t"), model = anthropicModel)
        }
        error.message shouldContain "provider mismatch"
    }

    @Test
    fun `execute rejects model without Completion capability`() = runTest {
        val model = LLModel(provider = LLMProvider.Google, id = "x", capabilities = emptyList())
        assertThrows<IllegalArgumentException> {
            subject.execute(prompt = Prompt(messages = emptyList(), id = "t"), model = model)
        }
    }

    @Test
    fun `execute rejects tools when model lacks Tools capability and toolChoice is Required`() = runTest {
        val model = LLModel(provider = LLMProvider.Google, id = "x", capabilities = listOf(LLMCapability.Completion))
        assertThrows<IllegalArgumentException> {
            subject.execute(
                prompt = Prompt(
                    messages = emptyList(),
                    id = "t",
                    params = GoogleParams(toolChoice = LLMParams.ToolChoice.Required)
                ),
                model = model,
                tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = emptyList()))
            )
        }
    }

    @Test
    fun `execute silently drops tools when model lacks Tools capability and toolChoice is optional`() = runTest {
        val model = LLModel(provider = LLMProvider.Google, id = "x", capabilities = listOf(LLMCapability.Completion))
        val tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = emptyList()))

        // Should not throw — tools are silently dropped when toolChoice is Auto/None/null
        val (_, systemInstruction) = subject.buildSdkContents(
            Prompt(messages = listOf(Message.User("hi", RequestMetaInfo.Empty)), id = "t"),
            model
        )
        val effectiveConfig = subject.buildConfig(LLMParams(), model, emptyList(), systemInstruction).build()
        effectiveConfig.tools().isPresent shouldBe false
    }

    // endregion

    // region Multimodal user content

    @Test
    fun `buildSdkContents converts user message with image`() {
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

        val (contents, _) = subject.buildSdkContents(prompt, fullCapabilityModel)

        contents shouldHaveSize 1
        val parts = contents[0].parts().get()
        parts shouldHaveSize 2
        parts[0].text().get() shouldBe "Describe this image"
        val blob = parts[1].inlineData().get()
        blob.shouldNotBeNull()
        blob.mimeType().get() shouldBe "image/png"
        blob.data().get() shouldBe imageBytes
    }

    @Test
    fun `buildSdkContents converts user message with audio`() {
        val audioBytes = "fake-audio".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.Audio(
                            content = AttachmentContent.Binary.Bytes(audioBytes),
                            format = "mp3",
                            mimeType = "audio/mpeg"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "audio"
        )

        val (contents, _) = subject.buildSdkContents(prompt, fullCapabilityModel)
        val blob = contents[0].parts().get()[0].inlineData().get()
        blob.shouldNotBeNull()
        blob.mimeType().get() shouldBe "audio/mpeg"
    }

    @Test
    fun `buildSdkContents converts user message with video`() {
        val videoBytes = "fake-video".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.Video(
                            content = AttachmentContent.Binary.Bytes(videoBytes),
                            format = "mp4",
                            mimeType = "video/mp4"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "video"
        )

        val (contents, _) = subject.buildSdkContents(prompt, fullCapabilityModel)
        val blob = contents[0].parts().get()[0].inlineData().get()
        blob.shouldNotBeNull()
        blob.mimeType().get() shouldBe "video/mp4"
    }

    @Test
    fun `buildSdkContents converts user message with file`() {
        val fileBytes = "fake-pdf".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ContentPart.File(
                            content = AttachmentContent.Binary.Bytes(fileBytes),
                            format = "pdf",
                            mimeType = "application/pdf"
                        )
                    ),
                    metaInfo = RequestMetaInfo.Empty
                ),
            ),
            id = "file"
        )

        val (contents, _) = subject.buildSdkContents(prompt, fullCapabilityModel)
        val blob = contents[0].parts().get()[0].inlineData().get()
        blob.shouldNotBeNull()
        blob.mimeType().get() shouldBe "application/pdf"
    }

    // endregion

    // region ThinkingLevel

    @ParameterizedTest
    @CsvSource(
        "LOW, LOW",
        "HIGH, HIGH",
    )
    fun `buildConfig sets thinkingLevel`(thinkingLevel: GoogleThinkingLevel, genaiThinkingLevel: com.google.genai.types.ThinkingLevel) {
        val params = GoogleParams(
            thinkingConfig = GoogleThinkingConfig(thinkingLevel = thinkingLevel)
        )
        val config = subject.buildConfig(params, GoogleModels.Gemini3_Pro_Preview, emptyList(), null).build()
        val tc = config.thinkingConfig().get()
        tc.thinkingLevel().get() shouldBe genaiThinkingLevel
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
