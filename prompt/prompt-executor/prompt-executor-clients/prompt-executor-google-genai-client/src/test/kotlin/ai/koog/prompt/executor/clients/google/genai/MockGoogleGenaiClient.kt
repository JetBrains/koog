package ai.koog.prompt.executor.clients.google.genai

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.google.genai.AsyncModels
import com.google.genai.Client
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.GenerateContentResponseUsageMetadata
import com.google.genai.types.Part
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CompletableFuture

// region Mock Client

/**
 * Creates a mockk mock of [Client] with the `async.models` field chain wired up for stubbing.
 *
 * The Google GenAI Java SDK uses public Java **fields** (`Client.async`, `Client.Async.models`)
 * rather than getter methods. mockk creates subclass proxies that leave these fields null.
 * We set them via reflection so that `client.async.models.generateContent(...)` can be stubbed.
 *
 * @return Pair of (mocked Client, mocked AsyncModels) — stub `generateContent` on the AsyncModels.
 */
internal fun mockGoogleGenaiClient(): Pair<Client, AsyncModels> {
    val client = mockk<Client>(relaxed = true)
    val asyncModels = mockk<AsyncModels>()
    val asyncClient = mockk<Client.Async>(relaxed = true)

    setField(asyncClient, "models", asyncModels)
    setField(client, "async", asyncClient)

    return client to asyncModels
}

internal fun setField(target: Any, fieldName: String, value: Any) {
    var clazz: Class<*> = target.javaClass
    while (clazz != Any::class.java) {
        try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
            return
        } catch (_: NoSuchFieldException) {
            clazz = clazz.superclass ?: break
        }
    }
    throw NoSuchFieldException("Field '$fieldName' not found in hierarchy of ${target.javaClass}")
}

// endregion

// region Captured API call

/**
 * Holds the arguments captured from `AsyncModels.generateContent(...)`.
 */
internal class CapturedApiCall {
    lateinit var modelId: String
    lateinit var contents: List<Content>
    lateinit var config: GenerateContentConfig
}

/**
 * Stubs [AsyncModels.generateContent] to return [response] and capture the call arguments.
 */
internal fun AsyncModels.stubGenerateContent(response: GenerateContentResponse): CapturedApiCall {
    val captured = CapturedApiCall()
    every { generateContent(any<String>(), any<List<Content>>(), any<GenerateContentConfig>()) } answers {
        captured.modelId = firstArg()
        captured.contents = secondArg()
        captured.config = thirdArg()
        CompletableFuture.completedFuture(response)
    }
    return captured
}

// endregion

// region Response builders

/** Builds a minimal valid [GenerateContentResponse] with a single text candidate. */
internal fun textResponse(
    text: String,
    finishReason: String = "STOP",
    usageMetadata: GenerateContentResponseUsageMetadata? = null
): GenerateContentResponse {
    val builder = GenerateContentResponse.builder()
        .candidates(
            listOf(
                Candidate.builder()
                    .content(Content.builder().role("model").parts(Part.fromText(text)).build())
                    .finishReason(finishReason)
                    .build()
            )
        )
    usageMetadata?.let { builder.usageMetadata(it) }
    return builder.build()
}

// endregion

// region Test models

/**
 * Shared test model definitions. Unit tests use these instead of [ai.koog.prompt.executor.clients.google.GoogleModels]
 * so they don't depend on production model definitions that may change.
 */
internal object TestModels {
    val flash = LLModel(
        provider = LLMProvider.Google,
        id = "test-flash",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature)
    )
    val thinking = LLModel(
        provider = LLMProvider.Google,
        id = "test-thinking",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature, LLMCapability.Thinking)
    )
    val pro = LLModel(
        provider = LLMProvider.Google,
        id = "test-pro",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        )
    )
    val fullCapability = LLModel(
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
    val completionOnly = LLModel(
        provider = LLMProvider.Google,
        id = "test-completion-only",
        capabilities = listOf(LLMCapability.Completion)
    )
    val noCap = LLModel(
        provider = LLMProvider.Google,
        id = "test-no-cap",
        capabilities = emptyList()
    )
    val multiChoice = LLModel(
        provider = LLMProvider.Google,
        id = "test-multi",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.MultipleChoices)
    )
    val multiChoiceNoCompletion = LLModel(
        provider = LLMProvider.Google,
        id = "test-multi-no-completion",
        capabilities = listOf(LLMCapability.MultipleChoices)
    )
    val toolCapable = LLModel(
        provider = LLMProvider.Google,
        id = "test-tools",
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools, LLMCapability.ToolChoice)
    )
    val noEmbed = LLModel(
        provider = LLMProvider.Google,
        id = "test-no-embed",
        capabilities = listOf(LLMCapability.Completion)
    )

    /** All test models — pass to [CustomizedGoogleGenaiLLMClient] constructor. */
    val all: List<LLModel> = listOf(
        flash, thinking, pro, fullCapability,
        completionOnly, noCap, multiChoice, multiChoiceNoCompletion,
        toolCapable, noEmbed,
    )
}

// endregion
