package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SiliconFlowModelsTest {

    private val embeddingModels = listOf(
        SiliconFlowModels.Embeddings.BgeLarge_En_V1_5,
        SiliconFlowModels.Embeddings.BgeLarge_Zh_V1_5,
        SiliconFlowModels.Embeddings.BgeM3,
        SiliconFlowModels.Embeddings.BceEmbedding_Base_V1,
        SiliconFlowModels.Embeddings.ProBgeM3,
        SiliconFlowModels.Embeddings.Qwen3_Embedding_0_6B,
        SiliconFlowModels.Embeddings.Qwen3_Embedding_4B,
        SiliconFlowModels.Embeddings.Qwen3_Embedding_8B,
    )

    @Test
    fun testSupportedModelsUseSiliconFlowProvider() {
        SiliconFlowModels.supportedModels.forEach { model ->
            assertEquals(
                expected = LLMProvider.SiliconFlow,
                actual = model.provider,
                message = "Model ${model.id} must use SiliconFlow provider"
            )
        }
    }

    @Test
    fun testSupportedModelsHaveUniqueNonBlankIds() {
        val ids = SiliconFlowModels.supportedModels.map { it.id }
        assertTrue(ids.all { it.isNotBlank() }, "All supported model IDs must be non-blank")
        assertEquals(ids.size, ids.toSet().size, "Supported model IDs must be unique")
    }

    @Test
    fun testEmbeddingModelsCapabilityAndContextLength() {
        embeddingModels.forEach { model ->
            assertEquals(LLMProvider.SiliconFlow, model.provider)
            assertTrue(model.capabilities?.contains(LLMCapability.Embed) == true, "${model.id} must support Embed")
            assertTrue((model.contextLength ?: 0) > 0, "${model.id} must define a positive context length")
        }
    }

    @Test
    fun testAddCustomModelRejectsNonSiliconFlowProvider() {
        val invalidModel = ai.koog.prompt.llm.LLModel(
            provider = LLMProvider.OpenAI,
            id = "custom/invalid-model",
            capabilities = listOf(LLMCapability.Completion),
        )

        assertFailsWith<IllegalArgumentException> {
            SiliconFlowModels.addCustomModel(invalidModel)
        }
    }
}
