package ai.koog.integration.tests

import ai.koog.prompt.executor.ollama.client.findByNameOrNull
import ai.koog.prompt.llm.LLMCapability.*
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@ExtendWith(OllamaTestFixtureExtension::class)
class OllamaClientModelsIntegrationTest {
    companion object {
        @field:InjectOllamaTestFixture
        private lateinit var fixture: OllamaTestFixture
        private val client get() = fixture.client
    }

    @Test
    fun `ollama_test load models`() = runTest(timeout = 600.seconds) {
        val modelCards = client.getModels()

        val modelCard = modelCards.findByNameOrNull(OllamaModels.Meta.LLAMA_3_2.id)
        assertNotNull(modelCard)
    }

    @Test
    fun `ollama_test get model`() = runTest(timeout = 600.seconds) {
        val modelCard = client.getModelOrNull(OllamaModels.Meta.LLAMA_3_2.id)
        assertNotNull(modelCard)

        assertEquals("llama3.2:latest", modelCard.name)
        assertEquals("llama", modelCard.family)
        assertEquals(listOf("llama"), modelCard.families)
        assertEquals(2019393189, modelCard.size)
        assertEquals(3212749888, modelCard.parameterCount)
        assertEquals(131072, modelCard.contextLength)
        assertEquals(3072, modelCard.embeddingLength)
        assertEquals("Q4_K_M", modelCard.quantizationLevel)
        assertEquals(
            listOf(Completion, Tools, Temperature, Schema.JSON.Simple, Schema.JSON.Full),
            modelCard.capabilities
        )
    }

    @Test
    fun `ollama_test pull model`() = runTest(timeout = 600.seconds) {
        val beforePull = client.getModelOrNull("tinyllama")
        assertNull(beforePull)

        val afterPull = client.getModelOrNull("tinyllama", pullIfMissing = true)
        assertNotNull(afterPull)
    }
}
