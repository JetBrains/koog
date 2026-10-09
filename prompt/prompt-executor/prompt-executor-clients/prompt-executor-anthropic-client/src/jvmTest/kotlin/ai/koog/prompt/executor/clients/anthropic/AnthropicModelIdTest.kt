package ai.koog.prompt.executor.clients.anthropic

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which model id reaches the API for a model the version map does not hold.
 *
 * The map is keyed by the whole [LLModel] value, so it misses two kinds of model that a caller has
 * every right to build: one released after this library, and a predefined one that was copied with
 * a field changed. Both used to fail before a request left the process.
 */
class AnthropicModelIdTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val client = AnthropicLLMClient(apiKey = "test-key")
    private val prompt = Prompt.build("test") { user("Hello") }

    private fun modelIdSentFor(model: LLModel): String {
        val request = client.createAnthropicRequest(prompt, emptyList(), model, false)
        return json.parseToJsonElement(request).jsonObject["model"]!!.jsonPrimitive.content
    }

    @Test
    fun testAPredefinedModelKeepsItsPinnedVersion() {
        // The point of the map: `claude-sonnet-4-0` is sent as the dated snapshot it was pinned to,
        // and nothing here may change that.
        assertEquals(
            DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP.getValue(AnthropicModels.Sonnet_4),
            modelIdSentFor(AnthropicModels.Sonnet_4),
        )
    }

    @Test
    fun testAModelTheMapDoesNotKnowIsSentUnderItsOwnId() {
        // A model released after this version of the library. Anthropic resolves an alias like this
        // one to its current snapshot itself, which is what a caller who wrote an alias asked for.
        val newer = LLModel(
            provider = LLMProvider.Anthropic,
            id = "claude-opus-5",
            capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature, LLMCapability.Tools),
            contextLength = 1_000_000,
        )

        assertEquals("claude-opus-5", modelIdSentFor(newer))
    }

    @Test
    fun testACopiedPredefinedModelIsStillSentUnderItsId() {
        // The lookup is by the whole value, so changing any field makes a known model unknown. A
        // caller narrowing a context window has not asked for a different model.
        val narrowed = AnthropicModels.Sonnet_4.copy(contextLength = 100_000)

        assertEquals(AnthropicModels.Sonnet_4.id, modelIdSentFor(narrowed))
    }

    @Test
    fun testAModelOfAnotherProviderIsStillRefused() {
        // The fallback is about the version map, not about which provider a client serves. An
        // OpenAI model handed to the Anthropic client is a caller mistake and must stay one.
        val foreign = LLModel(
            provider = LLMProvider.OpenAI,
            id = "gpt-4o",
            capabilities = listOf(LLMCapability.Completion),
            contextLength = 128_000,
        )

        val failure = kotlin.runCatching { modelIdSentFor(foreign) }.exceptionOrNull()
        assertEquals(
            IllegalArgumentException::class,
            failure?.let { it::class },
            "an Anthropic client asked for an OpenAI model should say so, not send it",
        )
    }
}
