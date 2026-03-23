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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        config.labels().orElse(null) shouldBe mapOf("source" to "test")

        // System message extracted as systemInstruction
        systemInstruction.shouldNotBeNull()
        systemInstruction.parts().orElse(emptyList())[0].text().orElse(null) shouldBe "You are a helpful assistant"

        // Only user message in contents
        contents shouldHaveSize 1
        contents[0].role().orElse(null) shouldBe "user"
        contents[0].parts().orElse(emptyList())[0].text().orElse(null) shouldBe "What is 2+2?"

        // Config reflects params
        config.systemInstruction().orElse(null).shouldNotBeNull()
        config.temperature().orElse(null) shouldBe 0.7f
        config.maxOutputTokens().orElse(null) shouldBe 256
        config.automaticFunctionCalling().orElse(null)?.disable()?.orElse(false) shouldBe true
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
        systemInstruction.parts().orElse(emptyList())[0].text().orElse(null) shouldBe "You are a math tutor"

        contents shouldHaveSize 3
        contents[0].role().orElse(null) shouldBe "user"
        contents[0].parts().orElse(emptyList())[0].text().orElse(null) shouldBe "What is 2+2?"
        contents[1].role().orElse(null) shouldBe "model"
        contents[1].parts().orElse(emptyList())[0].text().orElse(null) shouldBe "4"
        contents[2].role().orElse(null) shouldBe "user"
        contents[2].parts().orElse(emptyList())[0].text().orElse(null) shouldBe "And 3+3?"
    }

    // endregion

    // region Scenario: tool calling flow

    @Test
    fun `tool calling - full prompt with reasoning + parallel calls + results produces correct grouping`() {
        val prompt = Prompt(
            messages = listOf(
                Message.System("You can use tools", RequestMetaInfo.Empty),
                Message.User("What's the weather in Paris and London?", RequestMetaInfo.Empty),
                Message.Reasoning(encrypted = "sig-abc", content = "", metaInfo = ResponseMetaInfo.Empty),
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
        contents[0].role().orElse(null) shouldBe "user"
        contents[0].parts().orElse(emptyList())[0].text()
            .orElse(null) shouldBe "What's the weather in Paris and London?"

        // Grouped tool calls
        val callParts = contents[1].parts().orElse(emptyList())
        callParts shouldHaveSize 2
        callParts[0].functionCall().orElse(null)!!.name().orElse(null) shouldBe "get_weather"
        callParts[0].thoughtSignature().orElse(null) shouldBe "sig-abc".toByteArray()
        callParts[1].functionCall().orElse(null)!!.name().orElse(null) shouldBe "get_weather"
        // Second call gets fallback signature (thinking model)
        callParts[1].thoughtSignature().orElse(null) shouldBe "context_engineering_is_the_way_to_go".toByteArray()

        // Grouped tool results
        val resultParts = contents[2].parts().orElse(emptyList())
        resultParts shouldHaveSize 2
        resultParts[0].functionResponse().orElse(null)!!.name().orElse(null) shouldBe "get_weather"
        resultParts[1].functionResponse().orElse(null)!!.name().orElse(null) shouldBe "get_weather"
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
        reasoning.encrypted shouldBe "resp-sig"
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

        val callPart = contents[1].parts().orElse(emptyList())[0]
        callPart.functionCall().orElse(null)!!.name().orElse(null) shouldBe "search"
        callPart.thoughtSignature().orElse(null).shouldBeNull()
    }

    // endregion

    // region Scenario: thinking model conversation

    @Test
    fun `thinking model - reasoning with content becomes thought part in request`() {
        val prompt = Prompt(
            messages = listOf(
                Message.User("Explain quantum computing", RequestMetaInfo.Empty),
                Message.Reasoning(
                    content = "Let me think about this...",
                    encrypted = "thought-sig-1",
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
        val thoughtPart = contents[1].parts().orElse(emptyList())[0]
        thoughtPart.text().orElse(null) shouldBe "Let me think about this..."
        thoughtPart.thought().orElse(false) shouldBe true
        thoughtPart.thoughtSignature().orElse(null) shouldBe "thought-sig-1".toByteArray()
        // Assistant response
        contents[2].parts().orElse(emptyList())[0].text().orElse(null) shouldBe "Quantum computing uses qubits..."
        // Follow-up user message
        contents[3].parts().orElse(emptyList())[0].text().orElse(null) shouldBe "Tell me more"
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
        reasoning.encrypted shouldBe "sig-x"
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

        config.responseMimeType().orElse(null) shouldBe "application/json"
        config.responseSchema().orElse(null).shouldNotBeNull()
        config.responseJsonSchema().orElse(null).shouldBeNull()
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

        config.responseMimeType().orElse(null) shouldBe "application/json"
        config.responseJsonSchema().orElse(null).shouldNotBeNull()
        config.responseSchema().orElse(null).shouldBeNull()
    }

    // endregion

    // region Scenario: multiple choices

    @Test
    fun `multiple choices - response with 3 candidates returns all with correct content`() {
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
            )
            .build()

        val choices = subject.processResponse(response)

        choices shouldHaveSize 3
        (choices[0][0] as Message.Assistant).content shouldBe "Answer A"
        (choices[1][0] as Message.Assistant).content shouldBe "Answer B"
        (choices[2][0] as Message.Assistant).content shouldBe "Answer C"
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
            (results[0] as Message.Assistant).parts[0].shouldBeInstanceOf<ai.koog.prompt.message.ContentPart.Image>()
        img.format shouldBe "png"
        img.mimeType shouldBe "image/png"
    }

    // endregion

    // region Config edge cases

    @Test
    fun `buildConfig passes system instruction to config`() {
        val sysContent = Content.builder().parts(Part.fromText("Be helpful")).build()
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Pro, emptyList(), sysContent).build()
        val si = config.systemInstruction().orElse(null)
        si.shouldNotBeNull()
        si.parts().orElse(emptyList())[0].text().orElse(null) shouldBe "Be helpful"
    }

    @Test
    fun `buildConfig with default LLMParams has null optional fields`() {
        val config = subject.buildConfig(LLMParams(), GoogleModels.Gemini2_5Pro, emptyList(), null).build()
        config.maxOutputTokens().orElse(null).shouldBeNull()
        config.temperature().orElse(null).shouldBeNull()
        config.automaticFunctionCalling().orElse(null)?.disable()?.orElse(false) shouldBe true
    }

    @Test
    fun `buildConfig omits temperature for model without Temperature capability`() {
        val noTempModel =
            LLModel(provider = LLMProvider.Google, id = "no-temp", capabilities = listOf(LLMCapability.Completion))
        val config = subject.buildConfig(GoogleParams(temperature = 0.5), noTempModel, emptyList(), null).build()
        config.temperature().orElse(null).shouldBeNull()
    }

    @Test
    fun `buildConfig sets thinkingConfig with budget and includeThoughts`() {
        val params = GoogleParams(thinkingConfig = GoogleThinkingConfig(includeThoughts = true, thinkingBudget = 99))
        val config = subject.buildConfig(params, GoogleModels.Gemini3_Pro_Preview, emptyList(), null).build()
        val tc = config.thinkingConfig().orElse(null)
        tc.shouldNotBeNull()
        tc.includeThoughts().orElse(false) shouldBe true
        tc.thinkingBudget().orElse(null) shouldBe 99
    }

    // endregion

    // region Capability validation

    @Test
    fun `execute rejects model without Completion capability`() = runTest {
        val model = LLModel(provider = LLMProvider.Google, id = "x", capabilities = emptyList())
        assertThrows<IllegalArgumentException> {
            subject.execute(prompt = Prompt(messages = emptyList(), id = "t"), model = model)
        }
    }

    @Test
    fun `execute rejects tools when model lacks Tools capability`() = runTest {
        val model = LLModel(provider = LLMProvider.Google, id = "x", capabilities = listOf(LLMCapability.Completion))
        assertThrows<IllegalArgumentException> {
            subject.execute(
                prompt = Prompt(messages = emptyList(), id = "t"),
                model = model,
                tools = listOf(ToolDescriptor(name = "t", description = "d", requiredParameters = emptyList()))
            )
        }
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
                        ai.koog.prompt.message.ContentPart.Text("Describe this image"),
                        ai.koog.prompt.message.ContentPart.Image(
                            content = ai.koog.prompt.message.AttachmentContent.Binary.Bytes(imageBytes),
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
        val parts = contents[0].parts().orElse(emptyList())
        parts shouldHaveSize 2
        parts[0].text().orElse(null) shouldBe "Describe this image"
        val blob = parts[1].inlineData().orElse(null)
        blob.shouldNotBeNull()
        blob.mimeType().orElse(null) shouldBe "image/png"
        blob.data().orElse(null) shouldBe imageBytes
    }

    @Test
    fun `buildSdkContents converts user message with audio`() {
        val audioBytes = "fake-audio".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ai.koog.prompt.message.ContentPart.Audio(
                            content = ai.koog.prompt.message.AttachmentContent.Binary.Bytes(audioBytes),
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
        val blob = contents[0].parts().orElse(emptyList())[0].inlineData().orElse(null)
        blob.shouldNotBeNull()
        blob.mimeType().orElse(null) shouldBe "audio/mpeg"
    }

    @Test
    fun `buildSdkContents converts user message with video`() {
        val videoBytes = "fake-video".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ai.koog.prompt.message.ContentPart.Video(
                            content = ai.koog.prompt.message.AttachmentContent.Binary.Bytes(videoBytes),
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
        val blob = contents[0].parts().orElse(emptyList())[0].inlineData().orElse(null)
        blob.shouldNotBeNull()
        blob.mimeType().orElse(null) shouldBe "video/mp4"
    }

    @Test
    fun `buildSdkContents converts user message with file`() {
        val fileBytes = "fake-pdf".toByteArray()
        val prompt = Prompt(
            messages = listOf(
                Message.User(
                    parts = listOf(
                        ai.koog.prompt.message.ContentPart.File(
                            content = ai.koog.prompt.message.AttachmentContent.Binary.Bytes(fileBytes),
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
        val blob = contents[0].parts().orElse(emptyList())[0].inlineData().orElse(null)
        blob.shouldNotBeNull()
        blob.mimeType().orElse(null) shouldBe "application/pdf"
    }

    // endregion

    // region ThinkingLevel

    @Test
    fun `buildConfig sets thinkingLevel LOW`() {
        val params = GoogleParams(
            thinkingConfig = GoogleThinkingConfig(thinkingLevel = GoogleThinkingLevel.LOW)
        )
        val config = subject.buildConfig(params, GoogleModels.Gemini3_Pro_Preview, emptyList(), null).build()
        val tc = config.thinkingConfig().orElse(null)
        tc.shouldNotBeNull()
        tc.thinkingLevel().orElse(null)?.toString() shouldBe "LOW"
    }

    @Test
    fun `buildConfig sets thinkingLevel HIGH`() {
        val params = GoogleParams(
            thinkingConfig = GoogleThinkingConfig(thinkingLevel = GoogleThinkingLevel.HIGH)
        )
        val config = subject.buildConfig(params, GoogleModels.Gemini3_Pro_Preview, emptyList(), null).build()
        val tc = config.thinkingConfig().orElse(null)
        tc.shouldNotBeNull()
        tc.thinkingLevel().orElse(null)?.toString() shouldBe "HIGH"
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
