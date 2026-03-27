package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import com.google.genai.Pager
import com.google.genai.types.ListModelsConfig
import com.google.genai.types.Model
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Tests for [GoogleGenaiLLMClient.models] — verifying that the SDK [Model] list
 * is correctly converted to Koog [ai.koog.prompt.llm.LLModel] instances.
 */
class GoogleGenaiModelsTest {

    private val delegate = mockk<com.google.genai.Client>(relaxed = true)
    private val mockSdkModels = mockk<com.google.genai.Models>()
    private val subject = CustomizedGoogleGenaiLLMClient(delegate)

    init {
        setField(delegate, "models", mockSdkModels)
    }

    private fun stubModelsList(vararg models: Model) {
        val pager = mockk<Pager<Model>>()
        every { pager.iterator() } returns models.toMutableList().iterator()
        every { mockSdkModels.list(any<ListModelsConfig>()) } returns pager
    }

    // region Known model resolution

    @Test
    fun `resolves known model by id from default GoogleModels`() = runTest {
        stubModelsList(Model.builder().name("models/gemini-2.5-flash").build())

        val result = subject.models()

        result shouldHaveSize 1
        result shouldContain GoogleModels.Gemini2_5Flash
    }

    @Test
    fun `resolves multiple known models preserving order`() = runTest {
        stubModelsList(
            Model.builder().name("models/gemini-2.5-flash").build(),
            Model.builder().name("models/gemini-2.5-pro").build(),
            Model.builder().name("models/gemini-embedding-001").build(),
        )

        val result = subject.models()

        result shouldBe listOf(
            GoogleModels.Gemini2_5Flash,
            GoogleModels.Gemini2_5Pro,
            GoogleModels.Embeddings.GeminiEmbedding001
        )
    }

    // endregion

    // region Name handling

    @Test
    fun `strips models prefix from name`() = runTest {
        stubModelsList(
            Model.builder().name("models/my-custom-model").supportedActions(listOf("generateContent")).build()
        )

        subject.models()[0].id shouldBe "my-custom-model"
    }

    @Test
    fun `model without name produces id unknown`() = runTest {
        stubModelsList(Model.builder().build())

        subject.models()[0].id shouldBe "unknown"
    }

    // endregion

    // region generateContent capabilities

    @Test
    fun `generateContent action infers Completion, Temperature, Tools, ToolChoice, MultipleChoices`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/new-chat-model")
                .supportedActions(listOf("generateContent", "countTokens"))
                .build()
        )

        val caps = subject.models()[0].capabilities
        caps shouldNotBeNull {
            this shouldContain LLMCapability.Completion
            this shouldContain LLMCapability.Temperature
            this shouldContain LLMCapability.Tools
            this shouldContain LLMCapability.ToolChoice
            this shouldContain LLMCapability.MultipleChoices
        }
    }

    @Test
    fun `model without generateContent does not get Completion`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/some-predict-model")
                .supportedActions(listOf("predict"))
                .build()
        )

        subject.models()[0].capabilities!! shouldNotContain LLMCapability.Completion
    }

    // endregion

    // region Embed capability

    @Test
    fun `embedContent action infers Embed capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/my-embed-model")
                .supportedActions(listOf("embedContent", "countTokens"))
                .inputTokenLimit(2048)
                .outputTokenLimit(1)
                .build()
        )

        val model = subject.models()[0]
        model.capabilities!! shouldContain LLMCapability.Embed
        model.capabilities!! shouldNotContain LLMCapability.Completion
    }

    @Test
    fun `embedding in model name infers Embed even without embedContent action`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/custom-embedding-v2")
                .supportedActions(listOf("countTokens"))
                .build()
        )

        subject.models()[0].capabilities!! shouldContain LLMCapability.Embed
    }

    // endregion

    // region Thinking capability

    @Test
    fun `thinking true infers Thinking capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/thinking-model")
                .supportedActions(listOf("generateContent"))
                .thinking(true)
                .build()
        )

        subject.models()[0].capabilities!! shouldContain LLMCapability.Thinking
    }

    @Test
    fun `thinking false omits Thinking capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/no-thinking-model")
                .supportedActions(listOf("generateContent"))
                .thinking(false)
                .build()
        )

        subject.models()[0].capabilities!! shouldNotContain LLMCapability.Thinking
    }

    @Test
    fun `thinking absent omits Thinking capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/plain-model")
                .supportedActions(listOf("generateContent"))
                .build()
        )

        subject.models()[0].capabilities!! shouldNotContain LLMCapability.Thinking
    }

    // endregion

    // region Name-based modality heuristics

    @Test
    fun `image in model name infers Vision Image capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/gemini-2.5-flash-image")
                .supportedActions(listOf("generateContent"))
                .build()
        )

        subject.models()[0].capabilities!! shouldContain LLMCapability.Vision.Image
    }

    @Test
    fun `audio in model name infers Audio capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/gemini-2.5-flash-native-audio-latest")
                .supportedActions(listOf("generateContent"))
                .build()
        )

        subject.models()[0].capabilities!! shouldContain LLMCapability.Audio
    }

    @Test
    fun `veo in model name infers Vision Video capability`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/veo-3.0-generate-001")
                .supportedActions(listOf("predictLongRunning"))
                .build()
        )

        subject.models()[0].capabilities!! shouldContain LLMCapability.Vision.Video
    }

    @Test
    fun `plain model name without modality keywords omits modality capabilities`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/gemma-3-27b-it")
                .supportedActions(listOf("generateContent"))
                .build()
        )

        val caps = subject.models()[0].capabilities!!
        caps shouldNotContain LLMCapability.Vision.Image
        caps shouldNotContain LLMCapability.Audio
        caps shouldNotContain LLMCapability.Vision.Video
    }

    // endregion

    // region Token limits

    @Test
    fun `maps inputTokenLimit to contextLength and outputTokenLimit to maxOutputTokens`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/sized-model")
                .supportedActions(listOf("generateContent"))
                .inputTokenLimit(1_048_576)
                .outputTokenLimit(65_536)
                .build()
        )

        val model = subject.models()[0]
        model.contextLength shouldBe 1_048_576L
        model.maxOutputTokens shouldBe 65_536L
    }

    @Test
    fun `absent token limits map to null`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/no-limits-model")
                .supportedActions(listOf("generateContent"))
                .build()
        )

        val model = subject.models()[0]
        model.contextLength shouldBe null
        model.maxOutputTokens shouldBe null
    }

    // endregion

    // region Provider assignment

    @Test
    fun `unknown model gets the client provider`() = runTest {
        stubModelsList(
            Model.builder()
                .name("models/custom-model")
                .supportedActions(listOf("generateContent"))
                .build()
        )

        subject.models()[0].provider shouldBe LLMProvider.Google
    }

    // endregion
}
